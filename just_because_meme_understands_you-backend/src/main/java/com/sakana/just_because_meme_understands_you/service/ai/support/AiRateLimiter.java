package com.sakana.just_because_meme_understands_you.service.ai.support;

import com.sakana.just_because_meme_understands_you.common.support.RateLimitHelper;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import org.redisson.api.RateIntervalUnit;
import org.springframework.stereotype.Component;

/**
 * 用户级 AI 搜索限流（Redisson 令牌桶）。
 */
@Component
public class AiRateLimiter {

    private static final String KEY_PREFIX = "meme:ai:rate:";

    private final RateLimitHelper rateLimitHelper;
    private final MemeAgentProperties properties;

    public AiRateLimiter(RateLimitHelper rateLimitHelper, MemeAgentProperties properties) {
        this.rateLimitHelper = rateLimitHelper;
        this.properties = properties;
    }

    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = KEY_PREFIX + userId;
        int limit = Math.max(1, properties.getAi().getRateLimitPerMinute());
        return rateLimitHelper.tryAcquire(key, limit, 1, RateIntervalUnit.MINUTES);
    }
}