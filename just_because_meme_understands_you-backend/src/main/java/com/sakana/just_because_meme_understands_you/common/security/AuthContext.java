package com.sakana.just_because_meme_understands_you.common.security;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

/** Web 请求中的当前登录身份上下文。 */
public final class AuthContext {

    /** JwtAuthInterceptor 写入请求属性的 key。 */
    public static final String ATTR_USER_ID = "userId";
    /** 认证适配器写入的当前角色。 */
    public static final String ATTR_ROLE = "role";
    /** 认证适配器写入的规范角色代码列表。 */
    public static final String ATTR_ROLES = "roles";

    private AuthContext() {
    }

    /** 获取当前登录用户 id，未登录返回 null。 */
    public static Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userId));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 获取当前登录用户 id，未登录或异常抛 401。 */
    public static Long requireCurrentUserId(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        return userId;
    }

    /** 当前 JWT 角色（可能为 null）。 */
    public static String currentRole(HttpServletRequest request) {
        Object role = request.getAttribute(ATTR_ROLE);
        return role == null ? null : String.valueOf(role);
    }

    /** 当前登录用户的规范角色代码列表。 */
    @SuppressWarnings("unchecked")
    public static List<String> currentRoles(HttpServletRequest request) {
        Object roles = request.getAttribute(ATTR_ROLES);
        return roles instanceof List<?> ? (List<String>) roles : List.of();
    }

    /** 同时写入请求身份和 Spring Security 权限上下文，供现有服务与方法授权复用。 */
    public static void establish(HttpServletRequest request, String userId, Collection<String> roleCodes) {
        List<String> normalizedRoles = roleCodes == null ? List.of() : roleCodes.stream()
                .map(AuthContext::normalizeRoleCode)
                .filter(role -> !role.isEmpty())
                .distinct()
                .toList();
        List<SimpleGrantedAuthority> authorities = normalizedRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_ROLES, normalizedRoles);
        request.setAttribute(ATTR_ROLE, authorities.isEmpty() ? null : authorities.get(0).getAuthority());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, authorities));
    }

    /** 把历史 ROLE_ 前缀和规范代码统一为 USER、ADMIN、SUPER_ADMIN。 */
    public static String normalizeRoleCode(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }
        return switch (role) {
            case "USER", "ADMIN", "SUPER_ADMIN" -> role;
            default -> "";
        };
    }
}
