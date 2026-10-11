package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiIngestService;
import com.sakana.just_because_meme_understands_you.modules.ai.application.AiIngestPublisher;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiContentHashSupport;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AiIngestBackfillVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiIngestServiceImpl implements IAiIngestService {

    private static final int DEFAULT_BACKFILL_LIMIT = 500;
    private static final int MAX_BACKFILL_LIMIT = 2000;

    private final MemeCatalogPort memeCatalogPort;
    private final AiIngestPublisher aiIngestPublisher;
    private final MemeAgentClient memeAgentClient;
    private final MemeAgentProperties properties;

    public AiIngestServiceImpl(MemeCatalogPort memeCatalogPort,
                               AiIngestPublisher aiIngestPublisher,
                               MemeAgentClient memeAgentClient,
                               MemeAgentProperties properties) {
        this.memeCatalogPort = memeCatalogPort;
        this.aiIngestPublisher = aiIngestPublisher;
        this.memeAgentClient = memeAgentClient;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return memeAgentClient.isConfigured();
    }

    @Override
    public boolean syncMeme(Long memeId) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        if (!memeAgentClient.isConfigured()) {
            log.warn("Skip AI ingest: agent not configured, memeId={}", memeId);
            return false;
        }
        MemeCatalogPort.MemeCard meme = memeCatalogPort.findById(memeId);
        if (meme == null || !Integer.valueOf(1).equals(meme.status())) {
            aiIngestPublisher.publishDeletedMeme(String.valueOf(memeId))
                    .subscribe(null, error -> log.warn("AI ingest delete failed memeId={}", memeId, error));
            return false;
        }
        publishMeme(meme).subscribe(null,
                error -> log.warn("AI ingest failed memeId={}", memeId, error));
        return true;
    }

    @Override
    public void synchronizeMemeIndex(Long memeId) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        if (!memeAgentClient.isConfigured()) {
            throw new IllegalStateException("MemeAgent 未配置，索引事件稍后重试");
        }
        MemeCatalogPort.MemeCard meme = memeCatalogPort.findById(memeId);
        if (meme == null || !Integer.valueOf(1).equals(meme.status())) {
            aiIngestPublisher.publishDeletedMeme(String.valueOf(memeId)).block();
            return;
        }
        publishMeme(meme).block();
    }

    @Override
    public void removeMeme(Long memeId) {
        if (memeId == null || memeId <= 0) {
            return;
        }
        if (!memeAgentClient.isConfigured()) {
            return;
        }
        aiIngestPublisher.publishDeletedMeme(String.valueOf(memeId))
                .subscribe(null, error -> log.warn("AI ingest delete failed memeId={}", memeId, error));
    }

    @Override
    public AiIngestBackfillVO backfillPublished(Integer limit) {
        if (!properties.getAi().isBackfillEnabled()) {
            throw new BizException(Result.CODE_FORBIDDEN, "AI 灌库回填未开启");
        }
        int pageSize = limit == null || limit <= 0 ? DEFAULT_BACKFILL_LIMIT : Math.min(limit, MAX_BACKFILL_LIMIT);
        AiIngestBackfillVO vo = new AiIngestBackfillVO();
        if (!memeAgentClient.isConfigured()) {
            vo.setSkipped(1);
            return vo;
        }

        List<MemeCatalogPort.MemeCard> memes = memeCatalogPort.findPublished(pageSize);
        int queued = 0;
        for (MemeCatalogPort.MemeCard meme : memes) {
            if (meme == null || meme.id() == null) {
                continue;
            }
            if (syncMeme(meme.id())) {
                queued++;
            }
        }
        vo.setScanned(memes.size());
        vo.setQueued(queued);
        return vo;
    }

    private Mono<Void> publishMeme(MemeCatalogPort.MemeCard meme) {
        Map<Long, List<MemeCatalogPort.MemeTagSummary>> groupedTags =
                memeCatalogPort.findTagsByMemeIds(List.of(meme.id()));
        List<String> tags = groupedTags.getOrDefault(meme.id(), List.of()).stream()
                .map(MemeCatalogPort.MemeTagSummary::name)
                .toList();
        String hash = AiContentHashSupport.hash(meme.name(), meme.introduction(), tags);
        return aiIngestPublisher.publishPublishedMeme(
                String.valueOf(meme.id()), meme.name(), meme.introduction(), tags, hash);
    }

}
