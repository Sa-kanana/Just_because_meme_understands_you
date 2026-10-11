package com.sakana.just_because_meme_understands_you.modules.comment.contract;

/** 评论模块发布的站内通知事件快照。 */
public record CommentNotificationEvent(
        Type type,
        Long receiverUserId,
        Long actorUserId,
        Long memeId,
        Long commentId,
        Long rootId,
        String memeName,
        String memeCover,
        String content) {

    /** 评论通知的业务类型。 */
    public enum Type {
        MEME_COMMENTED,
        COMMENT_REPLIED
    }
}
