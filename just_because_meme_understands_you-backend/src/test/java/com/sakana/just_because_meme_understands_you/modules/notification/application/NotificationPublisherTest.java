package com.sakana.just_because_meme_understands_you.modules.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.entity.UserNotification;
import com.sakana.just_because_meme_understands_you.modules.notification.infrastructure.persistence.mapper.UserNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private UserNotificationMapper userNotificationMapper;

    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher();
        ReflectionTestUtils.setField(publisher, "userNotificationMapper", userNotificationMapper);
        ReflectionTestUtils.setField(publisher, "objectMapper", new ObjectMapper());
    }

    @Test
    void storesCommentNotificationWithOutboxEventIdForIdempotency() {
        when(userNotificationMapper.insert(any(UserNotification.class))).thenReturn(1);

        publisher.publishCommentNotification(new CommentNotificationCommand(
                701L,
                "reply",
                18L,
                3L,
                45L,
                90L,
                80L,
                "梗名",
                "https://example.test/cover.png",
                "评论内容"));

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationMapper).insert(captor.capture());
        UserNotification notification = captor.getValue();
        assertEquals(701L, notification.getSourceEventId());
        assertEquals(18L, notification.getUserId());
        assertEquals(3L, notification.getActorId());
        assertEquals("reply", notification.getType());
        assertEquals(45L, notification.getTargetId());
        assertEquals(90L, notification.getRefId());
        assertEquals("reply_comment:90", notification.getGroupKey());
        assertEquals(0, notification.getIsRead());
    }

    @Test
    void duplicateOutboxEventDoesNotCreateAnotherNotification() {
        doThrow(new DuplicateKeyException("duplicate source_event_id"))
                .when(userNotificationMapper).insert(any(UserNotification.class));

        assertDoesNotThrow(() -> publisher.publishCommentNotification(new CommentNotificationCommand(
                701L, "comment", 18L, 3L, 45L, 90L, 90L,
                "梗名", null, "评论内容")));
    }
}
