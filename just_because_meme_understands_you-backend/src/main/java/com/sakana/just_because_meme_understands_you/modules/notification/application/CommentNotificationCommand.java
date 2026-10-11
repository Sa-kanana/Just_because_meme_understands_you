package com.sakana.just_because_meme_understands_you.modules.notification.application;

/** 将 Outbox 评论事件转换为通知模块可处理的命令。 */
public record CommentNotificationCommand(
        Long sourceEventId,
        String type,
        Long receiverUserId,
        Long actorUserId,
        Long memeId,
        Long commentId,
        Long rootId,
        String memeName,
        String memeCover,
        String content) {
}
