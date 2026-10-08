package com.sakana.just_because_meme_understands_you.common.security;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;

/** Web 请求中的当前登录身份上下文。 */
public final class AuthContext {

    /** JwtAuthInterceptor 写入请求属性的 key。 */
    public static final String ATTR_USER_ID = "userId";
    /** 认证适配器写入的当前角色。 */
    public static final String ATTR_ROLE = "role";

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
}
