package com.sakana.just_because_meme_understands_you.service.user.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AfterCommitExecutor;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Favorite folder list cache and default-folder metadata.
 */
@Component
public class FavoriteFolderCacheSupport {

    private static final long CACHE_MINUTES = 5L;
    private static final String FOLDERS_PREFIX = "user:folders:";
    private static final String DEFAULT_META_PREFIX = "user:favorite-folder:default-meta:";

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AfterCommitExecutor afterCommitExecutor;

    public String foldersKey(Long userId, boolean ownerView) {
        return FOLDERS_PREFIX + userId + ":" + (ownerView ? "self" : "guest");
    }

    public <T> T get(String key, TypeReference<T> typeReference) {
        try {
            String json = stringRedisTemplate.opsForValue().get(Objects.requireNonNull(key, "cache key"));
            return StringUtils.hasText(json) ? objectMapper.readValue(json, typeReference) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (StringUtils.hasText(json)) {
                stringRedisTemplate.opsForValue().set(
                        Objects.requireNonNull(key, "cache key"),
                        Objects.requireNonNull(json, "cache value"),
                        CACHE_MINUTES,
                        TimeUnit.MINUTES);
            }
        } catch (Exception ignored) {
            // Cache write failure must not break business flow.
        }
    }

    public void evictFolders(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        afterCommitExecutor.execute(() -> {
            try {
                stringRedisTemplate.delete(List.of(
                        foldersKey(userId, true),
                        foldersKey(userId, false)));
            } catch (Exception ignored) {
                // Cache eviction failure must not break business flow.
            }
        });
    }

    public DefaultFolderMeta readDefaultMeta(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(DEFAULT_META_PREFIX + userId);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, DefaultFolderMeta.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void saveDefaultMeta(Long userId, DefaultFolderMeta meta) {
        try {
            stringRedisTemplate.opsForValue().set(
                    DEFAULT_META_PREFIX + userId,
                    objectMapper.writeValueAsString(meta));
        } catch (Exception e) {
            throw new BizException(Result.CODE_ERROR, "保存默认收藏夹信息失败");
        }
    }

    public static class DefaultFolderMeta {
        public String name;
        public String description;
        public Integer isPublic;
    }
}
