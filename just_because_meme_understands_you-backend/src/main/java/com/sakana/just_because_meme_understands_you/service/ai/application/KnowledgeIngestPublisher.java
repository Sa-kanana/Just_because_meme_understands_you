package com.sakana.just_because_meme_understands_you.service.ai.application;

import com.sakana.just_because_meme_understands_you.dto.MemeAgentKnowledgeIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.service.ai.client.MemeAgentClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeIngestPublisher {

    @Resource
    private MemeAgentClient memeAgentClient;

    @Async
    public void publishPublished(AdminKnowledge knowledge) {
        if (knowledge == null || knowledge.getId() == null) {
            return;
        }
        if (!memeAgentClient.isConfigured()) {
            log.warn("MemeAgent 未配置，跳过知识灌库 docId={}", knowledge.getId());
            return;
        }
        MemeAgentKnowledgeIngestRequestDTO request = MemeAgentKnowledgeIngestRequestDTO.builder()
                .documents(Collections.singletonList(toDocument(knowledge, "published")))
                .build();
        memeAgentClient.ingestKnowledgeAsync(request)
                .doOnError(e -> log.error("知识灌库失败 docId={}", knowledge.getId(), e))
                .subscribe();
    }

    @Async
    public void publishDeleted(Long docId) {
        if (docId == null || docId <= 0) {
            return;
        }
        if (!memeAgentClient.isConfigured()) {
            log.warn("MemeAgent 未配置，跳过知识删向量 docId={}", docId);
            return;
        }
        MemeAgentKnowledgeIngestRequestDTO.KnowledgeDocument doc =
                MemeAgentKnowledgeIngestRequestDTO.KnowledgeDocument.builder()
                        .docId(String.valueOf(docId))
                        .title("deleted")
                        .content("deleted")
                        .category("")
                        .tags(Collections.emptyList())
                        .status("deleted")
                        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .contentHash("deleted-" + docId)
                        .build();
        MemeAgentKnowledgeIngestRequestDTO request = MemeAgentKnowledgeIngestRequestDTO.builder()
                .documents(Collections.singletonList(doc))
                .build();
        memeAgentClient.ingestKnowledgeAsync(request)
                .doOnError(e -> log.error("知识删向量失败 docId={}", docId, e))
                .subscribe();
    }

    private static MemeAgentKnowledgeIngestRequestDTO.KnowledgeDocument toDocument(
            AdminKnowledge knowledge, String status) {
        return MemeAgentKnowledgeIngestRequestDTO.KnowledgeDocument.builder()
                .docId(String.valueOf(knowledge.getId()))
                .title(knowledge.getTitle())
                .content(knowledge.getContent())
                .category(knowledge.getCategory() == null ? "" : knowledge.getCategory())
                .tags(parseTags(knowledge.getTags()))
                .status(status)
                .updatedAt(knowledge.getUpdateTime() == null
                        ? OffsetDateTime.now(ZoneOffset.UTC)
                        : knowledge.getUpdateTime().atOffset(ZoneOffset.UTC))
                .contentHash(knowledge.getContentHash())
                .build();
    }

    private static List<String> parseTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
