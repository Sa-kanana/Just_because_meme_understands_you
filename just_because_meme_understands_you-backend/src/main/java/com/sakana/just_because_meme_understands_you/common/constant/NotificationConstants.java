package com.sakana.just_because_meme_understands_you.common.constant;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 站内消息类型与分类常量。
 */
public final class NotificationConstants {

    /** tab：全部 */
    public static final String TAB_ALL = "all";
    /** tab：未读 */
    public static final String TAB_UNREAD = "unread";
    /** tab：互动 */
    public static final String TAB_INTERACT = "interact";
    /** tab：系统 */
    public static final String TAB_SYSTEM = "system";

    public static final String TARGET_MEME = "meme";
    public static final String TARGET_USER = "user";
    public static final String TARGET_COMMENT = "comment";

    public static final String JUMP_MEME_DETAIL = "memeDetail";
    public static final String JUMP_USER_PROFILE = "userProfile";

    /** 消息 type 枚举值 */
    public static final String TYPE_LIKE = "like";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_REPLY = "reply";
    public static final String TYPE_FAVORITE = "favorite";
    public static final String TYPE_FOLLOW = "follow";
    public static final String TYPE_COMMENT_LIKE = "comment_like";
    public static final String TYPE_SYSTEM = "system";

    /** 互动类消息类型 */
    public static final Set<String> INTERACT_TYPES = Set.of(
            TYPE_LIKE,
            TYPE_COMMENT,
            TYPE_REPLY,
            TYPE_FAVORITE,
            TYPE_FOLLOW,
            TYPE_COMMENT_LIKE
    );

    /** 系统类消息类型 */
    public static final Set<String> SYSTEM_TYPES = Set.of(
            "system",
            "audit",
            "review",
            "announcement"
    );

    private NotificationConstants() {
    }

    public static boolean isInteractType(String type) {
        return type != null && INTERACT_TYPES.contains(type.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isSystemType(String type) {
        return type != null && SYSTEM_TYPES.contains(type.trim().toLowerCase(Locale.ROOT));
    }

    public static Set<String> resolveTypeFilter(String type, String tab) {
        String normalizedType = type != null ? type.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedTab = tab != null ? tab.trim().toLowerCase(Locale.ROOT) : "";

        if (!normalizedType.isEmpty()) {
            return Set.of(normalizedType);
        }
        if (TAB_INTERACT.equals(normalizedTab)) {
            return INTERACT_TYPES;
        }
        if (TAB_SYSTEM.equals(normalizedTab)) {
            return SYSTEM_TYPES;
        }
        return Collections.emptySet();
    }

    public static boolean isUnreadTab(String tab) {
        return tab != null && TAB_UNREAD.equals(tab.trim().toLowerCase(Locale.ROOT));
    }

    public static Set<String> knownTabs() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(TAB_ALL);
        set.add(TAB_UNREAD);
        set.add(TAB_INTERACT);
        set.add(TAB_SYSTEM);
        return set;
    }
}
