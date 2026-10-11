package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentNotificationEvent;
import com.sakana.just_because_meme_understands_you.modules.notification.application.CommentNotificationCommand;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentNotificationOutboxHandlerTest {

    @Mock
    private NotificationPublisher notificationPublisher;

    @Test
    void convertsOutboxReplyEventIntoNotificationCommand() throws Exception {
        CommentNotificationOutboxHandler handler =
                new CommentNotificationOutboxHandler(new ObjectMapper(), notificationPublisher);
        CommentNotificationEvent payload = new CommentNotificationEvent(
                CommentNotificationEvent.Type.COMMENT_REPLIED,
                18L,
                3L,
                45L,
                90L,
                80L,
                "梗名",
                "https://example.test/cover.png",
                "回复内容");
        OutboxEvent event = new OutboxEvent();
        event.setId(701L);
        event.setEventType(CommentNotificationOutboxAdapter.COMMENT_REPLIED_EVENT);
        event.setPayload(new ObjectMapper().writeValueAsString(payload));

        handler.handle(event);

        ArgumentCaptor<CommentNotificationCommand> command =
                ArgumentCaptor.forClass(CommentNotificationCommand.class);
        verify(notificationPublisher).publishCommentNotification(command.capture());
        assertEquals(701L, command.getValue().sourceEventId());
        assertEquals("reply", command.getValue().type());
        assertEquals(18L, command.getValue().receiverUserId());
        assertEquals(90L, command.getValue().commentId());
        assertEquals(80L, command.getValue().rootId());
    }
}
