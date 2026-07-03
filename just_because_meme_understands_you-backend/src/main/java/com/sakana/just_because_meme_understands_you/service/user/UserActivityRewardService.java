package com.sakana.just_because_meme_understands_you.service.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户活跃度积分/经验值奖励（预留扩展点）。
 */
@Slf4j
@Service
public class UserActivityRewardService {

    /**
     * 评论成功后的活跃度奖励，后续可接入积分/等级体系。
     */
    public void rewardCommentActivity(Long userId, Long memeId) {
        if (userId == null || userId <= 0 || memeId == null || memeId <= 0) {
            return;
        }
        log.debug("评论活跃度奖励占位, userId={}, memeId={}", userId, memeId);
    }
}
