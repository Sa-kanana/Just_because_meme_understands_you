package com.sakana.just_because_meme_understands_you.common.support;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 统一限流工具（基于 Redisson RRateLimiter 令牌桶）。
 */
@Component
public class RateLimitHelper {

    private final RedissonClient redissonClient;

    public RateLimitHelper(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 尝试获取许可，超限时抛出 BizException。
     *
     * @param key       Redis Key（如 "rate:login:ip:192.168.1.1"）
     * @param limit     时间窗口内最大请求数
     * @param interval  时间窗口长度
     * @param unit      时间单位
     * @param errorMsg  超限时的错误提示
     */
    public void acquire(String key, int limit, int interval, RateIntervalUnit unit, String errorMsg) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, limit, interval, unit);
        if (!rateLimiter.tryAcquire(1)) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, errorMsg);
        }
    }

    /**
     * 尝试获取许可，返回 boolean（不抛异常）。
     *
     * @param key       Redis Key
     * @param limit     时间窗口内最大请求数
     * @param interval  时间窗口长度
     * @param unit      时间单位
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, int limit, int interval, RateIntervalUnit unit) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, limit, interval, unit);
        return rateLimiter.tryAcquire(1);
    }

    /**
     * 删除指定 Key 的限流器（如登录成功后清理 IP 限流）。
     */
    public void remove(String key) {
        redissonClient.getRateLimiter(key).delete();
    }
}