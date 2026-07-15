package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 关注 / 取消关注限流，防止刷计数。
 */
@Slf4j
@Component
public class FollowRateLimiter {

    private static final String RATE_KEY_PREFIX = "rate:follow:user:";
    private static final long WINDOW_SECONDS = 60L;

    @Value("${user.follow.rate-limit-per-minute:60}")
    private int rateLimitPerMinute;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void check(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            String key = RATE_KEY_PREFIX + userId;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count > rateLimitPerMinute) {
                throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时放行，避免关注功能整体不可用
            log.warn("关注限流检查失败，已放行: userId={}, err={}", userId, e.toString());
        }
    }
}
