package com.sakana.just_because_meme_understands_you.service.feedback;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 反馈提交限流：按用户 + IP，默认每分钟 3 次。
 */
@Component
public class FeedbackRateLimiter {

    private static final String RATE_KEY_PREFIX = "feedback:submit:rate:";
    private static final long RATE_WINDOW_SECONDS = 60L;

    @Value("${app.feedback.rate-limit-per-minute:3}")
    private int rateLimitPerMinute;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ClientIpResolver clientIpResolver;

    public void check(Long userId, HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后再提交反馈");
        }
        String clientIp = clientIpResolver.resolve(request);
        String rateKey = RATE_KEY_PREFIX + userId + ":" + clientIp;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(rateKey, RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > rateLimitPerMinute) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "提交过于频繁，请稍后再试");
        }
    }
}
