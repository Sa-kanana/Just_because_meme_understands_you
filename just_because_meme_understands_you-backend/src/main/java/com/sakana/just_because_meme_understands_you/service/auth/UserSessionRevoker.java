package com.sakana.just_because_meme_understands_you.service.auth;

import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 吊销用户 refresh token 会话，用于改密、强制下线等场景。
 */
@Component
public class UserSessionRevoker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void revokeAllByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String indexKey = AuthConstants.USER_REFRESH_INDEX_PREFIX + userId;
        String refreshMd5 = stringRedisTemplate.opsForValue().getAndDelete(indexKey);
        if (StringUtils.hasText(refreshMd5)) {
            stringRedisTemplate.delete(AuthConstants.REFRESH_TOKEN_PREFIX + refreshMd5);
        }
    }

    public void revokeRefreshToken(String refreshToken, String userId) {
        if (StringUtils.hasText(refreshToken)) {
            stringRedisTemplate.delete(AuthConstants.REFRESH_TOKEN_PREFIX + DigestUtil.md5Hex(refreshToken));
        }
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String indexKey = AuthConstants.USER_REFRESH_INDEX_PREFIX + userId;
        String indexedMd5 = stringRedisTemplate.opsForValue().get(indexKey);
        if (StringUtils.hasText(refreshToken)
                && StringUtils.hasText(indexedMd5)
                && indexedMd5.equals(DigestUtil.md5Hex(refreshToken))) {
            stringRedisTemplate.delete(indexKey);
        }
    }
}
