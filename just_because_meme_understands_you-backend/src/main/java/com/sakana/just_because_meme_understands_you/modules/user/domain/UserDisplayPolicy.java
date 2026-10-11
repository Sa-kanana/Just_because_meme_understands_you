package com.sakana.just_because_meme_understands_you.modules.user.domain;

import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import org.springframework.util.StringUtils;

/** 统一用户注销后的公开身份展示策略；历史内容继续保留，公开身份统一匿名化。 */
public final class UserDisplayPolicy {

    /** 已注销账号在公开内容中的固定显示名称。 */
    public static final String DELETED_USER_NAME = "已注销用户";

    private UserDisplayPolicy() {
    }

    /** 判断用户是否已经软删除。 */
    public static boolean isDeleted(User user) {
        return user != null && user.getDeletedAt() != null;
    }

    /** 返回公开展示名称；不存在的用户使用调用方提供的兜底名称。 */
    public static String displayName(User user, String fallbackName) {
        if (user == null) return fallbackName;
        if (isDeleted(user)) return DELETED_USER_NAME;
        return StringUtils.hasText(user.getNickname()) ? user.getNickname().trim() : fallbackName;
    }

    /** 已注销用户不再公开头像地址。 */
    public static String displayAvatar(User user) {
        return user == null || isDeleted(user) ? null : user.getAvatar();
    }

    /** 已注销用户不再公开个性签名。 */
    public static String displaySignature(User user) {
        return user == null || isDeleted(user) || user.getSignature() == null
                ? ""
                : user.getSignature().trim();
    }
}
