package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AdminKnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxFailureRecorderTest {

    private OutboxEventMapper eventMapper;
    private AdminKnowledgeMapper knowledgeMapper;
    private KnowledgeIndexOutboxHandler knowledgeHandler;
    private OutboxFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        eventMapper = mock(OutboxEventMapper.class);
        knowledgeMapper = mock(AdminKnowledgeMapper.class);
        knowledgeHandler = new KnowledgeIndexOutboxHandler(
                knowledgeMapper, mock(com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient.class),
                new ObjectMapper());
        recorder = new OutboxFailureRecorder(eventMapper, knowledgeHandler);
    }

    @Test
    void retryAndDocumentStateAreWrittenOnlyForCurrentLease() {
        OutboxEvent event = event(KnowledgeIndexOutboxHandler.UPSERT_EVENT);
        when(eventMapper.markFailed(eq(10L), eq(event.getLockedAt()), eq("RETRY"), eq(1),
                eq(event.getNextAttemptAt()), eq("temporary"))).thenReturn(1);

        boolean recorded = recorder.record(event, "RETRY", 1, event.getNextAttemptAt(), "temporary");

        assertTrue(recorded);
        verify(knowledgeMapper).updateIndexStateIfRevisionUnlessTerminal(31L, 4L, "PENDING", "temporary");
        var order = inOrder(eventMapper, knowledgeMapper);
        order.verify(eventMapper).markFailed(eq(10L), eq(event.getLockedAt()), eq("RETRY"), eq(1),
                eq(event.getNextAttemptAt()), eq("temporary"));
        order.verify(knowledgeMapper).updateIndexStateIfRevisionUnlessTerminal(31L, 4L, "PENDING", "temporary");
    }

    @Test
    void expiredLeaseDoesNotMutateDocumentState() {
        OutboxEvent event = event(KnowledgeIndexOutboxHandler.UPSERT_EVENT);
        when(eventMapper.markFailed(eq(10L), eq(event.getLockedAt()), eq("RETRY"), eq(1),
                eq(event.getNextAttemptAt()), eq("temporary"))).thenReturn(0);

        boolean recorded = recorder.record(event, "RETRY", 1, event.getNextAttemptAt(), "temporary");

        assertFalse(recorded);
        verify(knowledgeMapper, never()).updateIndexStateIfRevisionUnlessTerminal(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void retryExhaustionWritesDeadAndFailedStateInOrder() {
        OutboxEvent event = event(KnowledgeIndexOutboxHandler.DELETE_EVENT);
        when(eventMapper.markFailed(eq(10L), eq(event.getLockedAt()), eq("DEAD"), eq(4),
                eq(event.getNextAttemptAt()), eq("permanent"))).thenReturn(1);
        event.setAttempts(3);

        assertTrue(recorder.record(event, "DEAD", 4, event.getNextAttemptAt(), "permanent"));

        var order = inOrder(eventMapper, knowledgeMapper);
        order.verify(eventMapper).markFailed(eq(10L), eq(event.getLockedAt()), eq("DEAD"), eq(4),
                eq(event.getNextAttemptAt()), eq("permanent"));
        order.verify(knowledgeMapper).updateIndexStateIfRevisionUnlessTerminal(31L, 4L, "FAILED", "permanent");
    }

    private static OutboxEvent event(String type) {
        OutboxEvent event = new OutboxEvent();
        event.setId(10L);
        event.setAggregateId(31L);
        event.setEventType(type);
        event.setPayload("{\"revision\":4}");
        event.setLockedAt(LocalDateTime.now());
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(2));
        return event;
    }
}
