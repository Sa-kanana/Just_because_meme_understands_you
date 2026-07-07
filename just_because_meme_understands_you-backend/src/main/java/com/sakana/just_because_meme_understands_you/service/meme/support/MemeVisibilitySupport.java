package com.sakana.just_because_meme_understands_you.service.meme.support;

import com.sakana.just_because_meme_understands_you.entity.Meme;

import java.util.Objects;

/**
 * 梗公域可见性与预览权限：status=1 公开展示；status=2/3 仅发布者预览；status=4 不可见。
 */
public final class MemeVisibilitySupport {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_REVIEWING = 2;
    public static final int STATUS_OFFLINE = 3;
    public static final int STATUS_PURGED = 4;

    public enum ViewAccess {
        /** 公域可见，评论/收藏等功能开放 */
        PUBLIC,
        /** 发布者预览（审核中 / 已下架） */
        OWNER_PREVIEW,
        /** 不可查看 */
        FORBIDDEN
    }

    private MemeVisibilitySupport() {
    }

    public static ViewAccess resolveViewAccess(Meme meme, Long currentUserId) {
        if (meme == null || meme.getStatus() == null) {
            return ViewAccess.FORBIDDEN;
        }
        return switch (meme.getStatus()) {
            case STATUS_NORMAL -> ViewAccess.PUBLIC;
            case STATUS_REVIEWING, STATUS_OFFLINE -> isOwner(meme, currentUserId)
                    ? ViewAccess.OWNER_PREVIEW
                    : ViewAccess.FORBIDDEN;
            default -> ViewAccess.FORBIDDEN;
        };
    }

    public static boolean isOwner(Meme meme, Long currentUserId) {
        return meme != null
                && meme.getUserId() != null
                && currentUserId != null
                && currentUserId > 0
                && Objects.equals(meme.getUserId(), currentUserId);
    }

    public static boolean isCommentsEnabled(Meme meme, Long currentUserId) {
        return resolveViewAccess(meme, currentUserId) == ViewAccess.PUBLIC;
    }

    public static String statusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_NORMAL -> "正常";
            case STATUS_REVIEWING -> "审核中";
            case STATUS_OFFLINE -> "已下架";
            case STATUS_PURGED -> "已彻底删除";
            default -> "未知";
        };
    }
}

