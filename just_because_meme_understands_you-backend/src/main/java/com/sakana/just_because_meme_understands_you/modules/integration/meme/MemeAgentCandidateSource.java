package com.sakana.just_because_meme_understands_you.modules.integration.meme;

import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentCrawlRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentCrawlResponseDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCandidateSource;
import org.springframework.stereotype.Component;

import java.util.List;

/** 组合 AI Agent HTTP 客户端与梗图采集端口的集成适配器。 */
@Component
public class MemeAgentCandidateSource implements MemeCandidateSource {

    private final MemeAgentClient memeAgentClient;

    public MemeAgentCandidateSource(MemeAgentClient memeAgentClient) {
        this.memeAgentClient = memeAgentClient;
    }

    @Override
    public boolean isConfigured() {
        return memeAgentClient.isConfigured();
    }

    @Override
    public List<Candidate> fetchCandidates(int limit) {
        return fetchCandidates(limit, List.of());
    }

    @Override
    public List<Candidate> fetchCandidates(int limit, List<String> excludedBilibiliVideoIds) {
        MemeAgentCrawlRequestDTO request = MemeAgentCrawlRequestDTO.builder()
                .limit(limit)
                .includeMarkdown(true)
                .excludedBilibiliVideoIds(excludedBilibiliVideoIds)
                .build();
        MemeAgentCrawlResponseDTO response = memeAgentClient.crawlHotMemes(request).block();
        if (response == null || response.getCandidates() == null) {
            return List.of();
        }
        return response.getCandidates().stream()
                .filter(candidate -> candidate != null)
                .map(this::toCandidate)
                .toList();
    }

    private Candidate toCandidate(MemeAgentCrawlResponseDTO.Candidate candidate) {
        return new Candidate(candidate.getTitle(), candidate.getIntroduction(),
                candidate.getSourceUrl(), candidate.getImageUrl(), candidate.getDetailUrl());
    }
}
