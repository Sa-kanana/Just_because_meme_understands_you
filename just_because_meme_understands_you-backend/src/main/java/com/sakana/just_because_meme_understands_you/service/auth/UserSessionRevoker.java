package com.sakana.just_because_meme_understands_you.service.auth;

import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户会话管理：多设备 refresh 索引、令牌版本号吊销。
 * 用于改密、强制下线、账号设置页安全态查询。
 */
@Component
public class UserSessionRevoker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 登记一个 refresh 会话（多设备共存）。
     */
    public void registerSession(String refreshToken, String userId, long ttlMillis) {
        if (!StringUtils.hasText(refreshToken) || !StringUtils.hasText(userId) || ttlMillis <= 0) {
            return;
        }
        String refreshMd5 = DigestUtil.md5Hex(refreshToken);
        String refreshKey = AuthConstants.REFRESH_TOKEN_PREFIX + refreshMd5;
        String sessionsKey = AuthConstants.USER_SESSIONS_PREFIX + userId;
        String legacyIndexKey = AuthConstants.USER_REFRESH_INDEX_PREFIX + userId;

        stringRedisTemplate.opsForValue().set(refreshKey, userId, ttlMillis, TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForSet().add(sessionsKey, refreshMd5);
        stringRedisTemplate.expire(sessionsKey, ttlMillis, TimeUnit.MILLISECONDS);
        // 兼容旧读取路径：仍写入「最新」索引
        stringRedisTemplate.opsForValue().set(legacyIndexKey, refreshMd5, ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 吊销指定 refresh，并从会话集合中移除。
     */
    public void revokeRefreshToken(String refreshToken, String userId) {
        String refreshMd5 = null;
        if (StringUtils.hasText(refreshToken)) {
            refreshMd5 = DigestUtil.md5Hex(refreshToken);
            stringRedisTemplate.delete(AuthConstants.REFRESH_TOKEN_PREFIX + refreshMd5);
        }
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String sessionsKey = AuthConstants.USER_SESSIONS_PREFIX + userId;
        if (StringUtils.hasText(refreshMd5)) {
            stringRedisTemplate.opsForSet().remove(sessionsKey, refreshMd5);
        }
        String legacyIndexKey = AuthConstants.USER_REFRESH_INDEX_PREFIX + userId;
        String indexedMd5 = stringRedisTemplate.opsForValue().get(legacyIndexKey);
        if (StringUtils.hasText(refreshMd5) && refreshMd5.equals(indexedMd5)) {
            stringRedisTemplate.delete(legacyIndexKey);
        }
    }

    /**
     * 吊销用户全部 refresh 会话，并递增令牌版本使存量 access 立即失效。
     *
     * @return 被吊销的 refresh 会话数
     */
    public int revokeAllByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        Set<String> refreshMd5Set = collectSessionMd5(userId);
        int revoked = 0;
        for (String md5 : refreshMd5Set) {
            if (!StringUtils.hasText(md5)) {
                continue;
            }
            Boolean deleted = stringRedisTemplate.delete(AuthConstants.REFRESH_TOKEN_PREFIX + md5);
            if (Boolean.TRUE.equals(deleted)) {
                revoked++;
            }
        }
        stringRedisTemplate.delete(AuthConstants.USER_SESSIONS_PREFIX + userId);
        stringRedisTemplate.delete(AuthConstants.USER_REFRESH_INDEX_PREFIX + userId);
        bumpTokenVersion(userId);
        return revoked;
    }

    /**
     * 当前有效 refresh 会话数（含本机）。
     */
    public int countSessions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        return collectSessionMd5(userId).size();
    }

    /**
     * 除当前 refresh 外是否还有其他会话。
     */
    public boolean hasOtherSessions(String userId, String currentRefreshToken) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        Set<String> sessions = collectSessionMd5(userId);
        if (sessions.isEmpty()) {
            return false;
        }
        if (!StringUtils.hasText(currentRefreshToken)) {
            return sessions.size() > 1;
        }
        String currentMd5 = DigestUtil.md5Hex(currentRefreshToken);
        for (String md5 : sessions) {
            if (StringUtils.hasText(md5) && !md5.equals(currentMd5)) {
                return true;
            }
        }
        return false;
    }

    public long currentTokenVersion(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        String raw = stringRedisTemplate.opsForValue().get(AuthConstants.USER_TOKEN_VERSION_PREFIX + userId);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public long bumpTokenVersion(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        String key = AuthConstants.USER_TOKEN_VERSION_PREFIX + userId;
        Long next = stringRedisTemplate.opsForValue().increment(key);
        if (next == null) {
            next = 1L;
            stringRedisTemplate.opsForValue().set(key, "1");
        }
        // 版本号长期保留；与账号生命周期绑定，不做短 TTL
        return next;
    }

    /**
     * 校验 JWT 内嵌版本是否仍有效（缺失视为 0）。
     */
    public boolean isTokenVersionValid(String userId, Object claimVersion) {
        long expected = currentTokenVersion(userId);
        long actual = 0L;
        if (claimVersion instanceof Number number) {
            actual = number.longValue();
        } else if (claimVersion != null && StringUtils.hasText(String.valueOf(claimVersion))) {
            try {
                actual = Long.parseLong(String.valueOf(claimVersion).trim());
            } catch (NumberFormatException ignored) {
                actual = -1L;
            }
        }
        return actual >= expected;
    }

    private Set<String> collectSessionMd5(String userId) {
        Set<String> result = new HashSet<>();
        String sessionsKey = AuthConstants.USER_SESSIONS_PREFIX + userId;
        Set<String> members = stringRedisTemplate.opsForSet().members(sessionsKey);
        if (members != null) {
            for (String md5 : members) {
                if (StringUtils.hasText(md5) && Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey(AuthConstants.REFRESH_TOKEN_PREFIX + md5))) {
                    result.add(md5);
                } else if (StringUtils.hasText(md5)) {
                    stringRedisTemplate.opsForSet().remove(sessionsKey, md5);
                }
            }
        }
        String legacyMd5 = stringRedisTemplate.opsForValue().get(AuthConstants.USER_REFRESH_INDEX_PREFIX + userId);
        if (StringUtils.hasText(legacyMd5)
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(AuthConstants.REFRESH_TOKEN_PREFIX + legacyMd5))) {
            result.add(Objects.requireNonNull(legacyMd5));
        }
        return result;
    }
}
