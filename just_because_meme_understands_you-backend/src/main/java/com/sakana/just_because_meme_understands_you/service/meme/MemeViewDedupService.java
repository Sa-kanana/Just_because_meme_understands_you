package com.sakana.just_because_meme_understands_you.service.meme;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 浏览去重：同一访客在 TTL 内对同一梗只计一次有效浏览。
 */
@Component
public class MemeViewDedupService {

    private static final String DEDUP_KEY_PREFIX = "meme:view:dedup:";

    @Value("${meme.view.dedup-hours:24}")
    private long dedupHours;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @return true 表示首次浏览（应计入）；false 表示重复上报
     */
    public boolean tryAcquire(String viewerKey, long memeId) {
        if (viewerKey == null || viewerKey.isBlank() || memeId <= 0) {
            return false;
        }
        String key = DEDUP_KEY_PREFIX + viewerKey + ":" + memeId;
        long ttl = Math.max(1L, dedupHours);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", ttl, TimeUnit.HOURS);
        return Boolean.TRUE.equals(acquired);
    }
}
