package com.sakana.just_because_meme_understands_you.service.auth;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ClientIpResolver clientIpResolver;

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
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failKey, lockoutMinutes, TimeUnit.MINUTES);
        }
        if (count != null && count >= lockoutAfterFailures) {
            String lockKey = LOCK_PREFIX + normalizedEmail;
            stringRedisTemplate.opsForValue().set(lockKey, "1", lockoutMinutes, TimeUnit.MINUTES);
            stringRedisTemplate.delete(failKey);
        }
    }

    public void onSuccess(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        stringRedisTemplate.delete(FAIL_COUNT_PREFIX + normalizedEmail);
        stringRedisTemplate.delete(LOCK_PREFIX + normalizedEmail);
    }

    private void checkIpAllowed(HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        String rateKey = IP_RATE_PREFIX + clientIp;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(rateKey, IP_RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > loginRateLimitPerMinute) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "登录请求过于频繁，请稍后重试");
        }
    }

    private void checkAccountLock(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        String lockKey = LOCK_PREFIX + normalizedEmail;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "登录失败次数过多，请 " + lockoutMinutes + " 分钟后再试");
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
