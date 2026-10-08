package com.sakana.just_because_meme_understands_you.modules.user.application.follow;

import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import org.springframework.stereotype.Component;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimitAspect;

/**
 * 关注 / 取消关注限流，防止刷计数。
 */
@Component
public class FollowRateLimiter {

    /** 在关注业务校验通过后调用，实际限流由注解切面执行。 */
    @RateLimit(count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES,
            dimension = RateLimit.Dimension.CUSTOM,
            limitProperty = "user.follow.rate-limit-per-minute",
            key = "#p0",
            condition = "#p0 != null && #p0 > 0",
            failOpen = true,
            message = "操作过于频繁，请稍后重试")
    public void check(Long userId) {
        // 限流逻辑由 RateLimitAspect 统一执行。
    }
}
