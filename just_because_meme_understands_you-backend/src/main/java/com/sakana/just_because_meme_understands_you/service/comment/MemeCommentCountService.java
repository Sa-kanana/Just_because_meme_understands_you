package com.sakana.just_because_meme_understands_you.service.comment;

import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class MemeCommentCountService {

    static final String COUNT_PREFIX = "meme:stats:comment_count:";
    static final String PENDING_SYNC_KEY = "meme:stats:comment_count:pending";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MemeMapper memeMapper;

    public int getCommentCount(Long memeId) {
        if (memeId == null || memeId <= 0) {
            return 0;
        }
        String key = COUNT_PREFIX + memeId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cached)) {
            return Math.max(0, parseCount(cached));
        }
        return loadCountFromDb(memeId);
    }

    public void increment(Long memeId) {
        if (memeId == null || memeId <= 0) {
            return;
        }
        ensureCountInitialized(memeId);
        stringRedisTemplate.opsForValue().increment(COUNT_PREFIX + memeId);
        markPendingSync(memeId);
    }

    public void syncPendingToDb() {
        Set<String> memeIds = stringRedisTemplate.opsForSet().members(PENDING_SYNC_KEY);
        if (memeIds == null || memeIds.isEmpty()) {
            return;
        }
        for (String memeIdStr : memeIds) {
            if (!StringUtils.hasText(memeIdStr)) {
                continue;
            }
            try {
                syncOne(Long.parseLong(memeIdStr.trim()));
            } catch (Exception e) {
                log.warn("同步梗评论总数失败, memeId={}", memeIdStr, e);
            }
        }
    }

    private void syncOne(Long memeId) {
        String key = COUNT_PREFIX + memeId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached)) {
            stringRedisTemplate.opsForSet().remove(PENDING_SYNC_KEY, String.valueOf(memeId));
            return;
        }
        int count = Math.max(0, parseCount(cached));
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null) {
            stringRedisTemplate.opsForSet().remove(PENDING_SYNC_KEY, String.valueOf(memeId));
            return;
        }
        meme.setComments(count);
        memeMapper.updateById(meme);
        stringRedisTemplate.opsForSet().remove(PENDING_SYNC_KEY, String.valueOf(memeId));
    }

    private void ensureCountInitialized(Long memeId) {
        String key = COUNT_PREFIX + memeId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        int dbCount = loadCountFromDb(memeId);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount));
    }

    private int loadCountFromDb(Long memeId) {
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null || meme.getComments() == null) {
            return 0;
        }
        return Math.max(0, meme.getComments());
    }

    private void markPendingSync(Long memeId) {
        stringRedisTemplate.opsForSet().add(
                PENDING_SYNC_KEY,
                Objects.requireNonNull(String.valueOf(memeId))
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
