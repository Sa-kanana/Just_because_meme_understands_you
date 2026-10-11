package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentKnowledgeIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AdminKnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexOutboxHandlerTest {

    private AdminKnowledgeMapper knowledgeMapper;
    private MemeAgentClient memeAgentClient;
    private KnowledgeIndexOutboxHandler handler;

    @BeforeEach
    void setUp() {
        knowledgeMapper = mock(AdminKnowledgeMapper.class);
        memeAgentClient = mock(MemeAgentClient.class);
        handler = new KnowledgeIndexOutboxHandler(knowledgeMapper, memeAgentClient, new ObjectMapper());
    }

    @Test
    void waitsForMemeAgentAcknowledgementBeforeMarkingDocumentReady() {
        AdminKnowledge document = document(3L);
        when(knowledgeMapper.selectById(7L)).thenReturn(document);
        when(knowledgeMapper.claimIndexStateIfRevision(anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(memeAgentClient.isConfigured()).thenReturn(true);
        when(memeAgentClient.ingestKnowledgeAsync(any(MemeAgentKnowledgeIngestRequestDTO.class)))
                .thenReturn(Mono.empty());

        handler.handle(event(7L, 3L, KnowledgeIndexOutboxHandler.UPSERT_EVENT));

        var order = inOrder(memeAgentClient, knowledgeMapper);
        order.verify(memeAgentClient).ingestKnowledgeAsync(any(MemeAgentKnowledgeIngestRequestDTO.class));
        order.verify(knowledgeMapper).updateIndexStateIfRevision(7L, 3L, "READY", null);
    }

    @Test
    void staleOutboxEventDoesNotCallVectorServiceOrChangeCurrentStatus() {
        when(knowledgeMapper.selectById(7L)).thenReturn(document(4L));

        handler.handle(event(7L, 3L, KnowledgeIndexOutboxHandler.DELETE_EVENT));

        verify(memeAgentClient, never()).ingestKnowledgeAsync(any());
        verify(knowledgeMapper, never()).updateIndexStateIfRevision(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void sendsEmptyCategoryWhenAdminDocumentHasNoCategory() {
        AdminKnowledge document = document(3L);
        document.setCategory(null);
        when(knowledgeMapper.selectById(7L)).thenReturn(document);
        when(knowledgeMapper.claimIndexStateIfRevision(anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(memeAgentClient.isConfigured()).thenReturn(true);
        when(memeAgentClient.ingestKnowledgeAsync(any(MemeAgentKnowledgeIngestRequestDTO.class)))
                .thenReturn(Mono.empty());

        handler.handle(event(7L, 3L, KnowledgeIndexOutboxHandler.UPSERT_EVENT));

        ArgumentCaptor<MemeAgentKnowledgeIngestRequestDTO> request =
                ArgumentCaptor.forClass(MemeAgentKnowledgeIngestRequestDTO.class);
        verify(memeAgentClient).ingestKnowledgeAsync(request.capture());
        assertEquals("", request.getValue().getDocuments().get(0).getCategory());
    }

    @Test
    void terminalDocumentStateSkipsReplayedOutboxDelivery() {
        when(knowledgeMapper.selectById(7L)).thenReturn(document(3L));
        when(knowledgeMapper.claimIndexStateIfRevision(anyLong(), anyLong(), anyString()))
                .thenReturn(0);

        handler.handle(event(7L, 3L, KnowledgeIndexOutboxHandler.UPSERT_EVENT));

        verify(memeAgentClient, never()).ingestKnowledgeAsync(any());
        verify(knowledgeMapper, never()).updateIndexStateIfRevision(anyLong(), anyLong(), anyString(), any());
    }

    private static AdminKnowledge document(long revision) {
        AdminKnowledge document = new AdminKnowledge();
        document.setId(7L);
        document.setTitle("知识标题");
        document.setContent("知识正文");
        document.setCategory("FAQ");
        document.setTags("新手,规则");
        document.setContentSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        document.setStatus(1);
        document.setIndexRevision(revision);
        return document;
    }

    private static OutboxEvent event(long docId, long revision, String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(docId);
        event.setEventType(eventType);
        event.setPayload("{\"docId\":\"" + docId + "\",\"revision\":" + revision + "}");
        return event;
    }
}
