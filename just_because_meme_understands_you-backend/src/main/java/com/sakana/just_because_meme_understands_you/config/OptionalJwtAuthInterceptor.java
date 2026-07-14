package com.sakana.just_because_meme_understands_you.config;

import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.service.auth.UserSessionRevoker;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 公开接口可选登录：携带有效 access token 时写入 userId，不阻断匿名访问。
 */
@Component
public class OptionalJwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserSessionRevoker userSessionRevoker;

    public OptionalJwtAuthInterceptor(JwtUtil jwtUtil,
                                      StringRedisTemplate stringRedisTemplate,
                                      UserSessionRevoker userSessionRevoker) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userSessionRevoker = userSessionRevoker;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (AuthContext.currentUserId(request) != null) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true;
        }
        String token = authHeader.substring(7);
        String blacklistKey = AuthConstants.ACCESS_BLACKLIST_PREFIX + DigestUtil.md5Hex(token);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) {
            return true;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            if (!AuthConstants.TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(AuthConstants.CLAIM_TOKEN_TYPE)))) {
                return true;
            }
            String userId = claims.getSubject();
            if (!userSessionRevoker.isTokenVersionValid(userId, claims.get(AuthConstants.CLAIM_TOKEN_VERSION))) {
                return true;
            }
            request.setAttribute(AuthContext.ATTR_USER_ID, userId);
            request.setAttribute(AuthConstants.CLAIM_ROLE, claims.get(AuthConstants.CLAIM_ROLE));
        } catch (Exception ignored) {
            // 无效 token 按匿名处理
        }
        return true;
    }
}
