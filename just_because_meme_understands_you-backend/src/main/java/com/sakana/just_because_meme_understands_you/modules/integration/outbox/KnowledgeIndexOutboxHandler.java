package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentKnowledgeIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AdminKnowledgeMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 将管理员知识 outbox 事件投递给 MemeAgent，并在远端提交后更新当前版本状态。 */
@Component
public class KnowledgeIndexOutboxHandler {

    /** 中文注释：管理员知识向量 upsert 事件类型。 */
    public static final String UPSERT_EVENT = "KNOWLEDGE_INDEX_UPSERT";

    /** 中文注释：管理员知识向量删除事件类型。 */
    public static final String DELETE_EVENT = "KNOWLEDGE_INDEX_DELETE";

    private final AdminKnowledgeMapper knowledgeMapper;
    private final MemeAgentClient memeAgentClient;
    private final ObjectMapper objectMapper;

    public KnowledgeIndexOutboxHandler(AdminKnowledgeMapper knowledgeMapper,
                                       MemeAgentClient memeAgentClient,
                                       ObjectMapper objectMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.memeAgentClient = memeAgentClient;
        this.objectMapper = objectMapper;
    }

    /** 处理一个知识事件；MemeAgent 返回成功表示向量事务已经提交。 */
    public void handle(OutboxEvent event) {
        long docId = event.getAggregateId();
        long revision = readRevision(event);
        AdminKnowledge document = knowledgeMapper.selectById(docId);
        if (document == null || document.getIndexRevision() == null
                || document.getIndexRevision() != revision) {
            // 中文注释：较旧的 outbox 事件已被更高 revision 取代，不得覆盖当前索引状态。
            return;
        }

        boolean deleting = DELETE_EVENT.equals(event.getEventType());
        String processingStatus = deleting ? "DELETE_PENDING" : "PROCESSING";
        int claimed = knowledgeMapper.claimIndexStateIfRevision(docId, revision, processingStatus);
        if (claimed == 0) {
            return;
        }

        if (!memeAgentClient.isConfigured()) {
            throw new IllegalStateException("MemeAgent 未配置，知识索引事件稍后重试");
        }
        MemeAgentKnowledgeIngestRequestDTO.KnowledgeDocument payload =
                MemeAgentKnowledgeIngestRequestDTO.KnowledgeDocument.builder()
                        .docId(String.valueOf(document.getId()))
                        .title(document.getTitle())
                        .content(document.getContent())
                        .category(document.getCategory() == null ? "" : document.getCategory())
                        .tags(document.getTags() == null || document.getTags().isBlank()
                                ? List.of() : List.of(document.getTags().split(",")))
                        .status(deleting ? "deleted" : "published")
                        .updatedAt((document.getUpdateTime() == null
                                ? OffsetDateTime.now(ZoneOffset.UTC)
                                : document.getUpdateTime().atOffset(ZoneOffset.UTC)))
                        .contentHash(document.getContentHash())
                        .contentSha256(document.getContentSha256())
                        .revision(revision)
                        .build();
        MemeAgentKnowledgeIngestRequestDTO request = MemeAgentKnowledgeIngestRequestDTO.builder()
                .documents(List.of(payload))
                .build();

        memeAgentClient.ingestKnowledgeAsync(request).block();
        knowledgeMapper.updateIndexStateIfRevision(
                docId, revision, deleting ? "DELETED" : "READY", null);
    }

    /** 对达到发件箱最大重试次数的当前版本记录可见失败状态。 */
    public void markDead(OutboxEvent event, String error) {
        knowledgeMapper.updateIndexStateIfRevisionUnlessTerminal(
                event.getAggregateId(), readRevision(event), "FAILED", boundedError(error));
    }

    /** 将未耗尽重试次数的任务恢复为重试状态，并保存本次安全错误供管理端查看。 */
    public void markRetry(OutboxEvent event, String error) {
        String status = DELETE_EVENT.equals(event.getEventType()) ? "DELETE_PENDING" : "PENDING";
        knowledgeMapper.updateIndexStateIfRevisionUnlessTerminal(
                event.getAggregateId(), readRevision(event), status, boundedError(error));
    }

    private static String boundedError(String error) {
        String source = error == null || error.isBlank() ? "知识向量处理失败" : error;
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < source.length() && safe.length() < 1000; index++) {
            char value = source.charAt(index);
            safe.append(Character.isISOControl(value) ? ' ' : value);
        }
        return safe.toString().trim();
    }

    private long readRevision(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            return payload.path("revision").asLong(0L);
        } catch (Exception exception) {
            throw new IllegalArgumentException("知识索引事件缺少有效 revision", exception);
        }
    }
}
