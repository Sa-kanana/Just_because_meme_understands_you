package com.sakana.just_because_meme_understands_you.api.admin.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.auth.domain.AuthConstants;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;

/** 管理端接口鉴权适配。 */
public final class AdminAuthSupport {

    private AdminAuthSupport() {
    }

    public static Long requireAdmin(HttpServletRequest request) {
        return requireAdmin(request, null);
    }

    /** 要求管理员角色；仅在管理端配置允许名单时接受其中的用户 id。 */
    public static Long requireAdmin(HttpServletRequest request, Collection<Long> allowUserIds) {
        Long userId = AuthContext.requireCurrentUserId(request);
        if (AuthConstants.ROLE_ADMIN.equals(AuthContext.currentRole(request))) {
            return userId;
        }
        if (allowUserIds != null && allowUserIds.contains(userId)) {
            return userId;
        }
        throw new BizException(Result.CODE_FORBIDDEN, "需要管理员权限");
    }
}
