package com.sakana.just_because_meme_understands_you.modules.ai.application.support;

import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import org.springframework.stereotype.Component;

/**
 * 用户级 AI 搜索限流（Redisson 令牌桶）。
 */
@Component
public class AiRateLimiter {

    @RateLimit(count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES,
            dimension = RateLimit.Dimension.CUSTOM,
            limitProperty = "meme.ai.rate-limit-per-minute",
            key = "#p0",
            condition = "#p0 != null",
            message = "AI 搜索过于频繁，请稍后再试")
    public boolean tryAcquire(Long userId) {
        return userId != null;
    }
}
