package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.entity.UserStats;
import com.sakana.just_because_meme_understands_you.mapper.UserStatsMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

/**
 * 用户收藏总数缓存：高并发下通过 Redis INCR/DECR 维护计数，定时任务同步回 user_stats。
 */
@Slf4j
@Service
public class UserFavoriteCountService {

    static final String COUNT_PREFIX = "user:stats:favorite_count:";
    static final String PENDING_SYNC_KEY = "user:stats:favorite_count:pending";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserStatsMapper userStatsMapper;

    public int getFavoriteCount(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        String key = COUNT_PREFIX + userId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cached)) {
            return Math.max(0, parseCount(cached));
        }
        return loadCountFromDb(userId);
    }

    public void increment(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        ensureCountInitialized(userId);
        stringRedisTemplate.opsForValue().increment(COUNT_PREFIX + userId);
        markPendingSync(userId);
    }

    public void decrement(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        ensureCountInitialized(userId);
        String key = COUNT_PREFIX + userId;
        Long result = stringRedisTemplate.opsForValue().decrement(key);
        if (result != null && result < 0) {
            stringRedisTemplate.opsForValue().set(key, "0");
        }
        markPendingSync(userId);
    }

    public void syncPendingToDb() {
        Set<String> userIds = stringRedisTemplate.opsForSet().members(PENDING_SYNC_KEY);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userIdStr : userIds) {
            if (!StringUtils.hasText(userIdStr)) {
                continue;
            }
            try {
                syncOne(Long.parseLong(userIdStr.trim()));
            } catch (Exception e) {
                log.warn("同步收藏总数失败, userId={}", userIdStr, e);
            }
        }
    }

    private void syncOne(Long userId) {
        String key = COUNT_PREFIX + userId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached)) {
            stringRedisTemplate.opsForSet().remove(PENDING_SYNC_KEY, String.valueOf(userId));
            return;
        }
        int count = Math.max(0, parseCount(cached));
        UserStats stats = userStatsMapper.selectById(userId);
        if (stats == null) {
            stats = new UserStats();
            stats.setUserId(userId);
            stats.setMemeCount(0);
            stats.setLikeReceived(0);
            stats.setFollowCount(0);
            stats.setFansCount(0);
            stats.setFavoriteCount(count);
            userStatsMapper.insert(stats);
        } else {
            stats.setFavoriteCount(count);
            userStatsMapper.updateById(stats);
        }
        stringRedisTemplate.opsForSet().remove(PENDING_SYNC_KEY, String.valueOf(userId));
    }

    private void ensureCountInitialized(Long userId) {
        String key = COUNT_PREFIX + userId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        int dbCount = loadCountFromDb(userId);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount));
    }

    private int loadCountFromDb(Long userId) {
        UserStats stats = userStatsMapper.selectById(userId);
        if (stats == null || stats.getFavoriteCount() == null) {
            return 0;
        }
        return Math.max(0, stats.getFavoriteCount());
    }

    private void markPendingSync(Long userId) {
        stringRedisTemplate.opsForSet().add(
                PENDING_SYNC_KEY,
                Objects.requireNonNull(String.valueOf(userId))
        );
    }

    private int parseCount(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
