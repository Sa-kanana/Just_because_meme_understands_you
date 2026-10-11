package com.sakana.just_because_meme_understands_you.modules.ai.application;

import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 业务事件触发向量灌库（审核通过 / 更新 / 下架）。
 */
@Slf4j
@Component
public class AiIngestPublisher {

    private final MemeAgentClient memeAgentClient;

    public AiIngestPublisher(MemeAgentClient memeAgentClient) {
        this.memeAgentClient = memeAgentClient;
    }

    public Mono<Void> publishPublishedMeme(String memeId,
                                           String title,
                                           String introduction,
                                           List<String> tags,
                                           String contentHash) {
        if (!memeAgentClient.isConfigured() || !StringUtils.hasText(memeId)) {
            return Mono.empty();
        }
        MemeAgentIngestRequestDTO.IngestDocument doc = MemeAgentIngestRequestDTO.IngestDocument.builder()
                .memeId(memeId)
                .title(title)
                .introduction(introduction == null ? "" : introduction)
                .tags(tags == null ? List.of() : tags)
                .status("published")
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .contentHash(contentHash)
                .build();
        MemeAgentIngestRequestDTO request = MemeAgentIngestRequestDTO.builder()
                .documents(List.of(doc))
                .build();
        return memeAgentClient.ingestAsync(request)
                .doOnSuccess(v -> log.info("AI ingest completed memeId={}", memeId))
                .doOnError(e -> log.warn("AI ingest failed memeId={}", memeId, e));
    }

    public Mono<Void> publishDeletedMeme(String memeId) {
        if (!memeAgentClient.isConfigured() || !StringUtils.hasText(memeId)) {
            return Mono.empty();
        }
        MemeAgentIngestRequestDTO.IngestDocument doc = MemeAgentIngestRequestDTO.IngestDocument.builder()
                .memeId(memeId)
                .title("-")
                .status("deleted")
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .contentHash("deleted")
                .build();
        MemeAgentIngestRequestDTO request = MemeAgentIngestRequestDTO.builder()
                .documents(List.of(doc))
                .build();
        return memeAgentClient.ingestAsync(request)
                .doOnSuccess(v -> log.info("AI ingest delete completed memeId={}", memeId))
                .doOnError(e -> log.warn("AI ingest delete failed memeId={}", memeId, e));
    }
}
