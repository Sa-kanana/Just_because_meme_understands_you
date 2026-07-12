package com.sakana.just_because_meme_understands_you.service.oss;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * OSS 上传凭证接口限流，防止登录用户刷 policy 造成滥用。
 */
@Component
public class OssPolicyRateLimiter {

    private static final String RATE_KEY_PREFIX = "oss:policy:rate:";
    private static final long RATE_WINDOW_SECONDS = 60L;

    @Value("${oss.policy-rate-limit-per-minute:10}")
    private int policyRateLimitPerMinute;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ClientIpResolver clientIpResolver;

    public void check(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        String clientIp = clientIpResolver.resolve(request);
        String rateKey = RATE_KEY_PREFIX + userId + ":" + clientIp;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(rateKey, RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > policyRateLimitPerMinute) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "上传凭证请求过于频繁，请稍后重试");
        }
    }
}
