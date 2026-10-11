package com.sakana.just_because_meme_understands_you.modules.comment.application;

import com.sakana.just_because_meme_understands_you.modules.user.application.activity.UserActivityRewardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 评论事务提交后的非核心异步处理。 */
@Slf4j
@Service
public class MemeCommentAsyncHandler {

    @Resource
    private UserActivityRewardService userActivityRewardService;

    @Async("memeCommentExecutor")
    public void afterCommentCreated(Long memeId, Long userId) {
        try {
            userActivityRewardService.rewardCommentActivity(userId, memeId);
        } catch (Exception e) {
            log.warn("异步用户活跃度奖励失败, userId={}, memeId={}", userId, memeId, e);
        }
    }
}
