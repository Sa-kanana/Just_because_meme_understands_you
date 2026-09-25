package com.sakana.just_because_meme_understands_you.service.oss;

import com.sakana.just_because_meme_understands_you.common.support.RateLimitHelper;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    private ClientIpResolver clientIpResolver;

    @Resource
    private RateLimitHelper rateLimitHelper;

    public void check(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        String clientIp = clientIpResolver.resolve(request);
        String rateKey = RATE_KEY_PREFIX + userId + ":" + clientIp;

        rateLimitHelper.acquire(rateKey, policyRateLimitPerMinute,
                (int) RATE_WINDOW_SECONDS, RateIntervalUnit.SECONDS,
                "上传凭证请求过于频繁，请稍后重试");
    }
}