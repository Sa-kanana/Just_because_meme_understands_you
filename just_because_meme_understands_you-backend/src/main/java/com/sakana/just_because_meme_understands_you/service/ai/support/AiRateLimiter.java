package com.sakana.just_because_meme_understands_you.service.ai.support;

import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用户级 AI 搜索限流。
 */
@Component
public class AiRateLimiter {

    private static final String KEY_PREFIX = "meme:ai:rate:";

    private final StringRedisTemplate redisTemplate;
    private final MemeAgentProperties properties;

    public AiRateLimiter(StringRedisTemplate redisTemplate, MemeAgentProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        int limit = Math.max(1, properties.getAi().getRateLimitPerMinute());
        return count != null && count <= limit;
    }
}
