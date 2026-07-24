package com.sakana.just_because_meme_understands_you.service.meme;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
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
import java.util.Objects;

/**
 * 梗审核通过：status 2→1，并触发向量灌库。
 * 当前无独立管理员体系时，由运维/内部调用；公开前请加权限网关。
 */
@Slf4j
@Service
public class MemeApproveService {

    @Resource
    private MemeMapper memeMapper;

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
        if (meme == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_PURGED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_NORMAL)) {
            scheduleAfterCommit(() -> safeSync(memeId));
            return;
        }
        if (!Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_REVIEWING)
                && !Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "当前状态不可审核通过");
        }

        LocalDateTime now = LocalDateTime.now();
        meme.setStatus(MemeVisibilitySupport.STATUS_NORMAL);
        meme.setUpdateTime(now);
        memeMapper.updateById(meme);

        Long userId = meme.getUserId();
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
            safeSync(memeId);
        });
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
