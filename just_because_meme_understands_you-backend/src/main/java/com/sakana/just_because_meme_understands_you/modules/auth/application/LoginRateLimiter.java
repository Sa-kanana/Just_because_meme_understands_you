package com.sakana.just_because_meme_understands_you.modules.auth.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimitHelper;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
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

    private static final String FAIL_COUNT_PREFIX = "auth:login:fail:";
    private static final String LOCK_PREFIX = "auth:login:lock:";

    @Value("${auth.login-lockout-after-failures:5}")
    private int lockoutAfterFailures;

    @Value("${auth.login-lockout-minutes:15}")
    private long lockoutMinutes;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RateLimitHelper rateLimitHelper;

    @RateLimit(count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES,
            dimension = RateLimit.Dimension.IP,
            limitProperty = "auth.login-rate-limit-per-minute",
            message = "登录请求过于频繁，请稍后重试")
    public void checkAllowed(String email) {
        checkAccountLock(email);
    }

    public void onFailure(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        String failKey = FAIL_COUNT_PREFIX + normalizedEmail;
        long count = rateLimitHelper.incrementFixedWindow(failKey, lockoutMinutes, TimeUnit.MINUTES);
        if (count >= lockoutAfterFailures) {
            String lockKey = LOCK_PREFIX + normalizedEmail;
            RBucket<String> lockBucket = redissonClient.getBucket(lockKey);
            lockBucket.set("1", lockoutMinutes, TimeUnit.MINUTES);
            redissonClient.getAtomicLong(failKey).delete();
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
