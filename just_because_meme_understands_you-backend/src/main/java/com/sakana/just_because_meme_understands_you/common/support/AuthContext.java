package com.sakana.just_because_meme_understands_you.common.support;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * Web 层认证上下文工具，统一从请求属性中解析当前用户 id，
 * 并归一化路径上的长整型 id 参数，消除多个 Controller 中的重复代码。
 *
 * @author sakana
 */
public final class AuthContext {

    /** JwtAuthInterceptor 写入请求属性的 key */
    public static final String ATTR_USER_ID = "userId";

    private AuthContext() {
    }

    /**
     * 获取当前登录用户 id，未登录返回 null。
     */
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

    /**
     * 获取当前登录用户 id，未登录或异常抛 401。
     */
    public static Long requireCurrentUserId(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        return userId;
    }

    /**
     * 当前 JWT 角色（可能为 null）。
     */
    public static String currentRole(HttpServletRequest request) {
        Object role = request.getAttribute(AuthConstants.CLAIM_ROLE);
        return role == null ? null : String.valueOf(role);
    }

    /**
     * 要求管理员：ROLE_ADMIN，或命中运维白名单 userId。
     */
    public static Long requireAdmin(HttpServletRequest request, Collection<Long> allowUserIds) {
        Long userId = requireCurrentUserId(request);
        String role = currentRole(request);
        if (AuthConstants.ROLE_ADMIN.equals(role)) {
            return userId;
        }
        if (allowUserIds != null && allowUserIds.contains(userId)) {
            return userId;
        }
        throw new BizException(Result.CODE_FORBIDDEN, "需要管理员权限");
    }

    /**
     * 解析路径上的长整型 id，必须为正数。
     */
    public static Long parseLongId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不能为空");
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 格式错误");
        }
    }

    /**
     * 解析收藏夹 folderId；0 表示虚拟默认收藏夹。
     */
    public static long parseFolderId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不能为空");
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed < 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 格式错误");
        }
    }

    /**
     * 解析路径上的用户 id，仅校验格式，不校验是否为正数（兼容历史接口语义）。
     */
    public static Long parseUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不能为空");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 格式错误");
        }
    }
}
