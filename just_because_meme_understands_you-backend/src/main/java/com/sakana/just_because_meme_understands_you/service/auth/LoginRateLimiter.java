package com.sakana.just_because_meme_understands_you.service.auth;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.RateLimitHelper;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 登录暴力破解防护：按 IP 限流 + 按账号失败次数锁定。
 */
@Component
public class LoginRateLimiter {

    private static final String IP_RATE_PREFIX = "rate:login:ip:";
    private static final String FAIL_COUNT_PREFIX = "auth:login:fail:";
    private static final String LOCK_PREFIX = "auth:login:lock:";
    private static final long IP_RATE_WINDOW_SECONDS = 60L;

    @Value("${auth.login-rate-limit-per-minute:10}")
    private int loginRateLimitPerMinute;

    @Value("${auth.login-lockout-after-failures:5}")
    private int lockoutAfterFailures;

    @Value("${auth.login-lockout-minutes:15}")
    private long lockoutMinutes;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ClientIpResolver clientIpResolver;

    @Resource
    private RateLimitHelper rateLimitHelper;

    public void checkAllowed(HttpServletRequest request, String email) {
        checkIpAllowed(request);
        checkAccountLock(email);
    }

    public void onFailure(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        String failKey = FAIL_COUNT_PREFIX + normalizedEmail;
        RAtomicLong failCounter = redissonClient.getAtomicLong(failKey);
        long count = failCounter.incrementAndGet();
        if (count == 1L) {
            failCounter.expire(lockoutMinutes, TimeUnit.MINUTES);
        }
        if (count >= lockoutAfterFailures) {
            String lockKey = LOCK_PREFIX + normalizedEmail;
            RBucket<String> lockBucket = redissonClient.getBucket(lockKey);
            lockBucket.set("1", lockoutMinutes, TimeUnit.MINUTES);
            failCounter.delete();
        }
    }

    public void onSuccess(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        redissonClient.getAtomicLong(FAIL_COUNT_PREFIX + normalizedEmail).delete();
        redissonClient.getBucket(LOCK_PREFIX + normalizedEmail).delete();
    }

    private void checkIpAllowed(HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        String rateKey = IP_RATE_PREFIX + clientIp;
        rateLimitHelper.acquire(rateKey, loginRateLimitPerMinute,
                (int) IP_RATE_WINDOW_SECONDS, RateIntervalUnit.SECONDS,
                "登录请求过于频繁，请稍后重试");
    }

    private void checkAccountLock(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        String lockKey = LOCK_PREFIX + normalizedEmail;
        RBucket<String> lockBucket = redissonClient.getBucket(lockKey);
        if (lockBucket.isExists()) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS,
                    "登录失败次数过多，请 " + lockoutMinutes + " 分钟后再试");
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}