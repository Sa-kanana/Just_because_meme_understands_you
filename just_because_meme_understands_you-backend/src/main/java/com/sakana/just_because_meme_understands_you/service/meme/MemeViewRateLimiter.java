package com.sakana.just_because_meme_understands_you.service.meme;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 浏览上报限流，防止刷量。
 */
@Component
public class MemeViewRateLimiter {

    private static final String RATE_KEY_PREFIX = "meme:view:rate:";
    private static final long RATE_WINDOW_SECONDS = 60L;

    @Value("${meme.view.report-rate-limit-per-minute:30}")
    private int reportRateLimitPerMinute;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void check(String viewerKey) {
        if (viewerKey == null || viewerKey.isBlank()) {
            return;
        }
        String rateKey = RATE_KEY_PREFIX + viewerKey;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(rateKey, RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > reportRateLimitPerMinute) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "浏览上报过于频繁，请稍后重试");
        }
    }
}
