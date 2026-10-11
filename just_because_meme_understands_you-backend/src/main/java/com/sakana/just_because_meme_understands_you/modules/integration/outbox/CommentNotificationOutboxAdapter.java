package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentNotificationEvent;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentNotificationEventPort;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 将评论模块通知端口适配为当前事务中的 Outbox 事件。 */
@Component
public class CommentNotificationOutboxAdapter implements CommentNotificationEventPort {

    static final String MEME_COMMENTED_EVENT = "COMMENT_NOTIFICATION_MEME_COMMENTED";
    static final String COMMENT_REPLIED_EVENT = "COMMENT_NOTIFICATION_COMMENT_REPLIED";

    private final OutboxEventWriter writer;
    private final ObjectMapper objectMapper;

    public CommentNotificationOutboxAdapter(OutboxEventWriter writer, ObjectMapper objectMapper) {
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(CommentNotificationEvent event) {
        validate(event);
        if (Objects.equals(event.receiverUserId(), event.actorUserId())) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("评论通知事件序列化失败", exception);
        }
        writer.appendEvent("COMMENT", event.commentId(), resolveEventType(event.type()), payload);
    }

    private static void validate(CommentNotificationEvent event) {
        if (event == null || event.type() == null
                || !isPositive(event.receiverUserId())
                || !isPositive(event.actorUserId())
                || !isPositive(event.memeId())
                || !isPositive(event.commentId())) {
            throw new IllegalArgumentException("评论通知事件字段不完整");
        }
    }

    private static String resolveEventType(CommentNotificationEvent.Type type) {
        return switch (type) {
            case MEME_COMMENTED -> MEME_COMMENTED_EVENT;
            case COMMENT_REPLIED -> COMMENT_REPLIED_EVENT;
        };
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }
}
