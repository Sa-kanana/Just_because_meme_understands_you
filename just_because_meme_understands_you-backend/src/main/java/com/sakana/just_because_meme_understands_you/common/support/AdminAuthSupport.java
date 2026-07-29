package com.sakana.just_because_meme_understands_you.common.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理端鉴权：仅 ROLE_ADMIN（不使用运维白名单）。
 */
public final class AdminAuthSupport {

    private AdminAuthSupport() {
    }

    public static Long requireAdmin(HttpServletRequest request) {
        return AuthContext.requireAdmin(request, null);
    }
}
