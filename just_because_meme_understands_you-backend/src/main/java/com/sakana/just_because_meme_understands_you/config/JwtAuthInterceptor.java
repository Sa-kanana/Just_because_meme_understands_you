package com.sakana.just_because_meme_understands_you.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private static final String ACCESS_BLACKLIST_PREFIX = "auth:blacklist:access:";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthInterceptor(JwtUtil jwtUtil,
                              StringRedisTemplate stringRedisTemplate,
                              ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
        String token = authHeader.substring(7);
        String blacklistKey = ACCESS_BLACKLIST_PREFIX + DigestUtil.md5Hex(token);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) {
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            if (!TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(TOKEN_TYPE_CLAIM)))) {
                writeUnauthorized(response, "无效的访问令牌");
                return false;
            }
            request.setAttribute("userId", claims.getSubject());
            request.setAttribute("role", claims.get("role"));
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "无效的令牌，请重新登录");
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.fail(Result.CODE_UNAUTHORIZED, message);
        String json = objectMapper.writeValueAsString(body);
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}

