package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentNotificationEvent;
import com.sakana.just_because_meme_understands_you.modules.notification.application.CommentNotificationCommand;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.NotificationConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 消费 Outbox 中的评论通知事件，并交由通知模块持久化。 */
@Component
public class CommentNotificationOutboxHandler {

    private final ObjectMapper objectMapper;
    private final NotificationPublisher notificationPublisher;

    public CommentNotificationOutboxHandler(
            ObjectMapper objectMapper,
            NotificationPublisher notificationPublisher) {
        this.objectMapper = objectMapper;
        this.notificationPublisher = notificationPublisher;
    }

    public void handle(OutboxEvent outboxEvent) {
        if (outboxEvent == null || outboxEvent.getId() == null
                || !StringUtils.hasText(outboxEvent.getPayload())) {
            throw new IllegalArgumentException("评论通知 Outbox 事件无效");
        }
        CommentNotificationEvent event;
        try {
            event = objectMapper.readValue(outboxEvent.getPayload(), CommentNotificationEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("评论通知 Outbox 载荷格式无效", exception);
        }
        String notificationType = resolveNotificationType(outboxEvent.getEventType(), event.type());
        notificationPublisher.publishCommentNotification(new CommentNotificationCommand(
                outboxEvent.getId(),
                notificationType,
                event.receiverUserId(),
                event.actorUserId(),
                event.memeId(),
                event.commentId(),
                event.rootId(),
                event.memeName(),
                event.memeCover(),
                event.content()));
    }

    private static String resolveNotificationType(String eventType, CommentNotificationEvent.Type payloadType) {
        if (CommentNotificationOutboxAdapter.MEME_COMMENTED_EVENT.equals(eventType)
                && payloadType == CommentNotificationEvent.Type.MEME_COMMENTED) {
            return NotificationConstants.TYPE_COMMENT;
        }
        if (CommentNotificationOutboxAdapter.COMMENT_REPLIED_EVENT.equals(eventType)
                && payloadType == CommentNotificationEvent.Type.COMMENT_REPLIED) {
            return NotificationConstants.TYPE_REPLY;
        }
        throw new IllegalArgumentException("评论通知 Outbox 事件类型与载荷不匹配");
    }
}
