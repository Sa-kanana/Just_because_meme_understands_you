package com.sakana.just_because_meme_understands_you.service.comment;

import com.sakana.just_because_meme_understands_you.service.user.UserActivityRewardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 评论发表后的非核心异步处理：Redis 计数、用户活跃度奖励等。
 */
@Slf4j
@Service
public class MemeCommentAsyncHandler {

    @Resource
    private MemeCommentCountService memeCommentCountService;

    @Resource
    private UserActivityRewardService userActivityRewardService;

    @Async("memeCommentExecutor")
    public void afterCommentCreated(Long memeId, Long userId) {
        try {
            memeCommentCountService.increment(memeId);
        } catch (Exception e) {
            log.warn("异步更新梗评论 Redis 计数失败, memeId={}", memeId, e);
        }
        try {
            userActivityRewardService.rewardCommentActivity(userId, memeId);
        } catch (Exception e) {
            log.warn("异步用户活跃度奖励失败, userId={}, memeId={}", userId, memeId, e);
        }
    }
}
