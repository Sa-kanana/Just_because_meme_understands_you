package com.sakana.just_because_meme_understands_you.service.meme;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.service.ai.IAiIngestService;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 审核通过：首次发布(2→1) 或 恢复审核(6→1)。
 */
@Slf4j
@Service
public class MemeApproveService {

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Resource
    private MemePublishAsyncHandler memePublishAsyncHandler;

    @Resource
    private IAiIngestService aiIngestService;

    @Resource
    private IMemeService memeService;

    @Resource
    private IUserProfileService userProfileService;

    @Transactional(rollbackFor = Exception.class)
    public void approveToPublished(Long memeId) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null || Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_PURGED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_NORMAL)) {
            scheduleAfterCommit(() -> safeSync(memeId));
            return;
        }
        if (!MemeVisibilitySupport.isReviewQueue(meme.getStatus())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅审核中或恢复审核中的梗可通过");
        }

        LocalDateTime now = LocalDateTime.now();
        meme.setStatus(MemeVisibilitySupport.STATUS_NORMAL);
        meme.setUpdateTime(now);
        meme.setAppealRejectCount(0);
        meme.setOfflineReason(null);
        memeMapper.updateById(meme);

        Long userId = meme.getUserId();
        List<Integer> tagIds = loadTagIdsByMemeId(memeId);
        scheduleAfterCommit(() -> {
            try {
                memeService.evictMemeDetailCache(memeId);
            } catch (Exception e) {
                log.warn("清理详情缓存失败 memeId={}", memeId, e);
            }
            if (userId != null) {
                try {
                    userProfileService.evictUserMemesCache(userId);
                } catch (Exception e) {
                    log.warn("清理用户梗缓存失败 userId={}", userId, e);
                }
            }
            try {
                memePublishAsyncHandler.incrementTagRelatedQuantity(tagIds);
            } catch (Exception e) {
                log.warn("审核通过后标签计数失败 memeId={}", memeId, e);
            }
            safeSync(memeId);
        });
    }

    private List<Integer> loadTagIdsByMemeId(Long memeId) {
        List<MemeTagRelation> relations = memeTagRelationMapper.selectList(
                new LambdaQueryWrapper<MemeTagRelation>()
                        .eq(MemeTagRelation::getMemeId, memeId.intValue())
        );
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<Integer> tagIds = new ArrayList<>(relations.size());
        for (MemeTagRelation relation : relations) {
            if (relation != null && relation.getMemeTagId() != null && relation.getMemeTagId() > 0) {
                tagIds.add(relation.getMemeTagId());
            }
        }
        return tagIds;
    }

    private void safeSync(Long memeId) {
        try {
            aiIngestService.syncMeme(memeId);
        } catch (Exception e) {
            log.warn("审核通过后灌库失败 memeId={}", memeId, e);
        }
    }

    private void scheduleAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
