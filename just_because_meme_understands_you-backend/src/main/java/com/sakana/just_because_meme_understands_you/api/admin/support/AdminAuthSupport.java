package com.sakana.just_because_meme_understands_you.api.admin.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.auth.domain.AuthConstants;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/** 管理端接口鉴权适配。 */
public final class AdminAuthSupport {

    private AdminAuthSupport() {
    }

    /** 要求当前用户具有 ADMIN 或 SUPER_ADMIN 角色。 */
    public static Long requireAdmin(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        List<String> roles = AuthContext.currentRoles(request);
        String legacyRole = AuthContext.currentRole(request);
        if (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")
                || AuthConstants.ROLE_ADMIN.equals(legacyRole)
                || AuthConstants.ROLE_SUPER_ADMIN.equals(legacyRole)) {
            return userId;
        }
        throw new BizException(Result.CODE_FORBIDDEN, "需要管理员权限");
    }
}
