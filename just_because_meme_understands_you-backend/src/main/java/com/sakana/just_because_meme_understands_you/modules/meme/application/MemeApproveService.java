package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
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
    private MemeIndexEventPort memeIndexEventPort;

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
            memeIndexEventPort.requestUpsert(memeId);
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
        memeIndexEventPort.requestUpsert(memeId);

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
        });
    }

    private List<Integer> loadTagIdsByMemeId(Long memeId) {
        List<MemeTagRelation> relations = memeTagRelationMapper.selectList(
                new LambdaQueryWrapper<MemeTagRelation>()
                        .eq(MemeTagRelation::getMemeId, memeId)
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
