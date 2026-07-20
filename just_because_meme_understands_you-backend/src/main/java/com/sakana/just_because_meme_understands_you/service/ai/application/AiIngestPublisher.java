package com.sakana.just_because_meme_understands_you.service.ai.application;

import com.sakana.just_because_meme_understands_you.dto.MemeAgentIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.service.ai.client.MemeAgentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    @Async
    public void publishPublishedMeme(String memeId,
                                     String title,
                                     String introduction,
                                     List<String> tags,
                                     String contentHash) {
        if (!memeAgentClient.isConfigured() || !StringUtils.hasText(memeId)) {
            return;
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
        memeAgentClient.ingestAsync(request)
                .doOnSuccess(v -> log.info("AI ingest queued memeId={}", memeId))
                .doOnError(e -> log.warn("AI ingest failed memeId={}", memeId, e))
                .subscribe();
    }

    @Async
    public void publishDeletedMeme(String memeId) {
        if (!memeAgentClient.isConfigured() || !StringUtils.hasText(memeId)) {
            return;
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
        memeAgentClient.ingestAsync(request)
                .doOnSuccess(v -> log.info("AI ingest delete queued memeId={}", memeId))
                .doOnError(e -> log.warn("AI ingest delete failed memeId={}", memeId, e))
                .subscribe();
    }
}
