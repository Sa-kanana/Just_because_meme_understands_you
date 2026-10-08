package com.sakana.just_because_meme_understands_you.common.ratelimit;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 原子计数工具，仅用于登录连续失败次数锁定；请求限流规则统一由 {@code @RateLimit} 处理。
 */
@Component
public class RateLimitHelper {

    private final RedissonClient redissonClient;

    public RateLimitHelper(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 增加固定窗口计数，并在首次计数时设置过期时间，用于登录失败锁定。
     *
     * @param key      Redis Key
     * @param interval 窗口长度
     * @param unit     时间单位
     * @return 当前窗口内的请求数
     */
    public long incrementFixedWindow(String key, long interval, TimeUnit unit) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long count = counter.incrementAndGet();
        if (count == 1L) {
            counter.expire(interval, unit);
        }
        return count;
    }

}
