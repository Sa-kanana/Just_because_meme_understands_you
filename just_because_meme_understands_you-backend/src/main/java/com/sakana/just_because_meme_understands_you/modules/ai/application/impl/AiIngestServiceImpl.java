package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiIngestService;
import com.sakana.just_because_meme_understands_you.modules.ai.application.AiIngestPublisher;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiContentHashSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiIngestServiceImpl implements IAiIngestService {

    private final MemeCatalogPort memeCatalogPort;
    private final AiIngestPublisher aiIngestPublisher;
    private final MemeAgentClient memeAgentClient;
    public AiIngestServiceImpl(MemeCatalogPort memeCatalogPort,
                               AiIngestPublisher aiIngestPublisher,
                               MemeAgentClient memeAgentClient) {
        this.memeCatalogPort = memeCatalogPort;
        this.aiIngestPublisher = aiIngestPublisher;
        this.memeAgentClient = memeAgentClient;
    }

    @Override
    public boolean isAvailable() {
        return memeAgentClient.isConfigured();
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
