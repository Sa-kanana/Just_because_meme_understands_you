package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiIngestService;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 中文注释：验证 Outbox 事件投递成功后确认，失败时保留重试。 */
@ExtendWith(MockitoExtension.class)
class OutboxEventRelayTest {

    @Mock
    private OutboxClaimService claimService;

    @Mock
    private OutboxEventMapper mapper;

    @Mock
    private IAiIngestService aiIngestService;

    @Mock
    private CommentNotificationOutboxHandler commentNotificationOutboxHandler;

    @Mock
    private KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler;

    @Mock
    private OutboxFailureRecorder outboxFailureRecorder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MemeAgentProperties memeAgentProperties;

    @InjectMocks
    private OutboxEventRelay relay;

    @Test
    void upsertEventIsDeliveredThroughAiApplicationContract() {
        OutboxEvent event = event("MEME_INDEX_UPSERT");
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        when(aiIngestService.isAvailable()).thenReturn(true);
        when(mapper.markDelivered(eq(10L), eq(event.getLockedAt()), any(LocalDateTime.class))).thenReturn(1);

        int delivered = relay.relayOnce();

        assertEquals(1, delivered);
        verify(aiIngestService).synchronizeMemeIndex(31L);
        verify(mapper).markDelivered(eq(10L), eq(event.getLockedAt()), any(LocalDateTime.class));
        verify(outboxFailureRecorder, never()).record(any(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    void unavailableAiServiceLeavesEventRetryable() {
        OutboxEvent event = event("MEME_INDEX_REMOVE");
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        when(aiIngestService.isAvailable()).thenReturn(false);

        int delivered = relay.relayOnce();

        assertEquals(0, delivered);
        verify(aiIngestService, never()).synchronizeMemeIndex(anyLong());
        ArgumentCaptor<LocalDateTime> nextAttempt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxFailureRecorder).record(eq(event), eq("RETRY"), eq(1), nextAttempt.capture(),
                contains("不可用"));
        verify(mapper, never()).markDelivered(anyLong(), any(), any());
    }

    @Test
    void downstreamFailureIsRetriedInsteadOfAcknowledged() {
        OutboxEvent event = event("MEME_INDEX_UPSERT");
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        when(aiIngestService.isAvailable()).thenReturn(true);
        doThrow(new IllegalStateException("MemeAgent timeout"))
                .when(aiIngestService).synchronizeMemeIndex(31L);

        int delivered = relay.relayOnce();

        assertEquals(0, delivered);
        verify(outboxFailureRecorder).record(eq(event), eq("RETRY"), eq(1), any(LocalDateTime.class),
                contains("timeout"));
        verify(mapper, never()).markDelivered(anyLong(), any(), any());
    }

    @Test
    void knowledgeIndexFailureBecomesDeadAfterThreeRetries() {
        OutboxEvent event = event(KnowledgeIndexOutboxHandler.UPSERT_EVENT);
        event.setAttempts(3);
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("知识向量进度保存失败"))
                .when(knowledgeIndexOutboxHandler).handle(event);

        int delivered = relay.relayOnce();

        assertEquals(0, delivered);
        verify(outboxFailureRecorder).record(eq(event), eq("DEAD"), eq(4), any(LocalDateTime.class),
                contains("知识向量进度保存失败"));
        verify(knowledgeIndexOutboxHandler, never()).markRetry(any(), anyString());
        verify(mapper, never()).markDelivered(anyLong(), any(), any());
    }

    @Test
    void knowledgeIndexFailureStillSchedulesThirdRetryAfterInitialAttempt() {
        OutboxEvent event = event(KnowledgeIndexOutboxHandler.UPSERT_EVENT);
        event.setAttempts(2);
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("知识向量进度保存失败"))
                .when(knowledgeIndexOutboxHandler).handle(event);

        int delivered = relay.relayOnce();

        assertEquals(0, delivered);
        verify(outboxFailureRecorder).record(eq(event), eq("RETRY"), eq(3), any(LocalDateTime.class),
                contains("知识向量进度保存失败"));
        verify(knowledgeIndexOutboxHandler, never()).markDead(any(), anyString());
    }

    @Test
    void commentNotificationIsDeliveredWithoutAiService() {
        OutboxEvent event = event("COMMENT_NOTIFICATION_COMMENT_REPLIED");
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        when(mapper.markDelivered(eq(10L), eq(event.getLockedAt()), any(LocalDateTime.class))).thenReturn(1);

        int delivered = relay.relayOnce();

        assertEquals(1, delivered);
        verify(commentNotificationOutboxHandler).handle(event);
        verify(aiIngestService, never()).isAvailable();
        verify(mapper).markDelivered(eq(10L), eq(event.getLockedAt()), any(LocalDateTime.class));
        verify(outboxFailureRecorder, never()).record(any(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    void commentNotificationFailureRemainsRetryable() {
        OutboxEvent event = event("COMMENT_NOTIFICATION_MEME_COMMENTED");
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("通知库暂不可用"))
                .when(commentNotificationOutboxHandler).handle(event);

        int delivered = relay.relayOnce();

        assertEquals(0, delivered);
        verify(outboxFailureRecorder).record(eq(event), eq("RETRY"), eq(1), any(LocalDateTime.class),
                contains("通知库暂不可用"));
        verify(mapper, never()).markDelivered(anyLong(), any(), any());
    }

    @Test
    void doesNotRegressKnowledgeStateWhenAnotherLeaseAlreadyAcknowledgedEvent() {
        OutboxEvent event = event(KnowledgeIndexOutboxHandler.UPSERT_EVENT);
        when(claimService.claim(anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("late worker failure"))
                .when(knowledgeIndexOutboxHandler).handle(event);
        when(outboxFailureRecorder.record(eq(event), eq("RETRY"), eq(1), any(LocalDateTime.class), anyString()))
                .thenReturn(false);

        int delivered = relay.relayOnce();

        assertEquals(0, delivered);
        verify(knowledgeIndexOutboxHandler, never()).markRetry(eq(event), anyString());
        verify(knowledgeIndexOutboxHandler, never()).markDead(any(), anyString());
    }

    private OutboxEvent event(String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setId(10L);
        event.setAggregateId(31L);
        event.setEventType(eventType);
        event.setAttempts(0);
        event.setStatus("PROCESSING");
        event.setLockedAt(LocalDateTime.now());
        return event;
    }
}
