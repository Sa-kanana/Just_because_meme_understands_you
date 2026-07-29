package com.sakana.just_because_meme_understands_you.service.meme.support;

import com.sakana.just_because_meme_understands_you.entity.Meme;

import java.util.Objects;
import java.util.Set;

/**
 * 梗公域可见性与生命周期状态。
 *
 * <pre>
 * 1 正常
 * 2 首次发布审核中
 * 3 主动下架（可随时重新上架 → 1）
 * 4 永久封禁 / 彻底删除
 * 5 风控下架锁定（需提交整改申诉 → 6）
 * 6 恢复审核中（通过 → 1；驳回 → 5，多次驳回 → 4）
 * </pre>
 */
public final class MemeVisibilitySupport {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_REVIEWING = 2;
    public static final int STATUS_OFFLINE = 3;
    public static final int STATUS_PURGED = 4;
    public static final int STATUS_LOCKED = 5;
    public static final int STATUS_RESTORE_REVIEWING = 6;

    /** 本人列表可见（不含永久封禁） */
    public static final Set<Integer> OWNER_VISIBLE_STATUSES = Set.of(
            STATUS_NORMAL,
            STATUS_REVIEWING,
            STATUS_OFFLINE,
            STATUS_LOCKED,
            STATUS_RESTORE_REVIEWING
    );

    /** 仅发布者可预览的非公域状态 */
    public static final Set<Integer> OWNER_PREVIEW_STATUSES = Set.of(
            STATUS_REVIEWING,
            STATUS_OFFLINE,
            STATUS_LOCKED,
            STATUS_RESTORE_REVIEWING
    );

    public enum ViewAccess {
        PUBLIC,
        OWNER_PREVIEW,
        FORBIDDEN
    }

    private MemeVisibilitySupport() {
    }

    public static ViewAccess resolveViewAccess(Meme meme, Long currentUserId) {
        if (meme == null || meme.getStatus() == null) {
            return ViewAccess.FORBIDDEN;
        }
        int status = meme.getStatus();
        if (status == STATUS_NORMAL) {
            return ViewAccess.PUBLIC;
        }
        if (OWNER_PREVIEW_STATUSES.contains(status)) {
            return isOwner(meme, currentUserId) ? ViewAccess.OWNER_PREVIEW : ViewAccess.FORBIDDEN;
        }
        return ViewAccess.FORBIDDEN;
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

    public static boolean isOwnerPreviewStatus(Integer status) {
        return status != null && OWNER_PREVIEW_STATUSES.contains(status);
    }

    public static boolean canVoluntaryRelist(Integer status) {
        return Objects.equals(status, STATUS_OFFLINE);
    }

    public static boolean canSubmitAppeal(Integer status) {
        return Objects.equals(status, STATUS_LOCKED);
    }

    public static boolean isReviewQueue(Integer status) {
        return Objects.equals(status, STATUS_REVIEWING)
                || Objects.equals(status, STATUS_RESTORE_REVIEWING);
    }

    public static String statusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_NORMAL -> "正常";
            case STATUS_REVIEWING -> "审核中";
            case STATUS_OFFLINE -> "已下架";
            case STATUS_PURGED -> "已永久封禁";
            case STATUS_LOCKED -> "下架锁定";
            case STATUS_RESTORE_REVIEWING -> "恢复审核中";
            default -> "未知";
        };
    }
}
