package com.sakana.just_because_meme_understands_you.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.dto.MemeTagBindDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.service.ai.IAiIngestService;
import com.sakana.just_because_meme_understands_you.service.ai.application.AiIngestPublisher;
import com.sakana.just_because_meme_understands_you.service.ai.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.service.ai.support.AiContentHashSupport;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.vo.AiIngestBackfillVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiIngestServiceImpl implements IAiIngestService {

    private static final int DEFAULT_BACKFILL_LIMIT = 500;
    private static final int MAX_BACKFILL_LIMIT = 2000;

    private final MemeMapper memeMapper;
    private final MemeTagRelationMapper memeTagRelationMapper;
    private final AiIngestPublisher aiIngestPublisher;
    private final MemeAgentClient memeAgentClient;
    private final MemeAgentProperties properties;

    public AiIngestServiceImpl(MemeMapper memeMapper,
                               MemeTagRelationMapper memeTagRelationMapper,
                               AiIngestPublisher aiIngestPublisher,
                               MemeAgentClient memeAgentClient,
                               MemeAgentProperties properties) {
        this.memeMapper = memeMapper;
        this.memeTagRelationMapper = memeTagRelationMapper;
        this.aiIngestPublisher = aiIngestPublisher;
        this.memeAgentClient = memeAgentClient;
        this.properties = properties;
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
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null) {
            aiIngestPublisher.publishDeletedMeme(String.valueOf(memeId));
            return false;
        }
        if (!Integer.valueOf(MemeVisibilitySupport.STATUS_NORMAL).equals(meme.getStatus())) {
            aiIngestPublisher.publishDeletedMeme(String.valueOf(memeId));
            return false;
        }
        List<String> tags = loadTagNames(meme.getId());
        String hash = AiContentHashSupport.hash(meme.getName(), meme.getIntroduction(), tags);
        aiIngestPublisher.publishPublishedMeme(
                String.valueOf(meme.getId()),
                meme.getName(),
                meme.getIntroduction(),
                tags,
                hash);
        return true;
    }

    @Override
    public void removeMeme(Long memeId) {
        if (memeId == null || memeId <= 0) {
            return;
        }
        if (!memeAgentClient.isConfigured()) {
            return;
        }
        aiIngestPublisher.publishDeletedMeme(String.valueOf(memeId));
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

        List<Meme> memes = memeMapper.selectList(new LambdaQueryWrapper<Meme>()
                .eq(Meme::getStatus, MemeVisibilitySupport.STATUS_NORMAL)
                .orderByAsc(Meme::getId)
                .last("LIMIT " + pageSize));
        int queued = 0;
        for (Meme meme : memes) {
            if (meme == null || meme.getId() == null) {
                continue;
            }
            if (syncMeme(meme.getId().longValue())) {
                queued++;
            }
        }
        vo.setScanned(memes.size());
        vo.setQueued(queued);
        return vo;
    }

    private List<String> loadTagNames(Integer memeId) {
        if (memeId == null) {
            return List.of();
        }
        List<MemeTagBindDTO> rows = memeTagRelationMapper.selectTagsByMemeIds(List.of(memeId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (MemeTagBindDTO row : rows) {
            if (row != null && StringUtils.hasText(row.getName())) {
                tags.add(row.getName().trim());
            }
        }
        return tags;
    }
}
