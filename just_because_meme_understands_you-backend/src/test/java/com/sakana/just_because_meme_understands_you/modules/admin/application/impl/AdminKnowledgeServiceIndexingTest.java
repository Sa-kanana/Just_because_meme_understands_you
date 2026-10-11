package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminKnowledgeSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminKnowledgeVO;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiContentHashSupport;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AdminKnowledgeMapper;
import com.sakana.just_because_meme_understands_you.modules.integration.outbox.OutboxEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AdminKnowledgeServiceIndexingTest {

    private AdminKnowledgeMapper knowledgeMapper;
    private OutboxEventWriter outboxEventWriter;
    private AdminKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        knowledgeMapper = mock(AdminKnowledgeMapper.class);
        outboxEventWriter = mock(OutboxEventWriter.class);
        service = new AdminKnowledgeServiceImpl();
        ReflectionTestUtils.setField(service, "adminKnowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "adminViewMapper", Mappers.getMapper(AdminViewMapper.class));
        ReflectionTestUtils.setField(service, "outboxEventWriter", outboxEventWriter);
    }

    @Test
    void createReusesExistingDocumentWhenNormalizedBodyMatches() {
        AdminKnowledge existing = document(41L, 1, "READY", 5L);
        when(knowledgeMapper.insertIfContentSha256Absent(any(AdminKnowledge.class))).thenReturn(0);
        when(knowledgeMapper.selectByContentSha256ForUpdate(anyString())).thenReturn(existing);

        AdminKnowledgeVO result = service.create(99L, request("规则正文", "新标题"));

        assertEquals(41L, result.getId());
        assertTrue(result.getDuplicate());
        assertEquals("原有标题", result.getTitle());
        verify(knowledgeMapper).insertIfContentSha256Absent(any(AdminKnowledge.class));
        verify(outboxEventWriter, never()).appendEvent(anyString(), eq(41L), anyString(), anyString());
    }

    @Test
    void createRestoresDeletedBodyWithNextRevisionAndDurableOutboxEvent() {
        AdminKnowledge existing = document(42L, 0, "DELETED", 8L);
        when(knowledgeMapper.insertIfContentSha256Absent(any(AdminKnowledge.class))).thenReturn(0);
        when(knowledgeMapper.selectByContentSha256ForUpdate(anyString())).thenReturn(existing);
        when(outboxEventWriter.appendEvent(eq("ADMIN_KNOWLEDGE"), eq(42L),
                eq("KNOWLEDGE_INDEX_UPSERT"), anyString())).thenReturn(77L);

        AdminKnowledgeVO result = service.create(99L, request("规则正文", "不会覆盖原标题"));

        assertTrue(result.getDuplicate());
        assertEquals(1, existing.getStatus());
        assertEquals("PENDING", existing.getIndexStatus());
        assertEquals(9L, existing.getIndexRevision());
        assertEquals(77L, existing.getIndexTaskId());
        verify(outboxEventWriter).appendEvent(eq("ADMIN_KNOWLEDGE"), eq(42L),
                eq("KNOWLEDGE_INDEX_UPSERT"), anyString());
    }

    @Test
    void createWritesDocumentAndOutboxInsideTheServiceTransactionBoundary() {
        doAnswer(invocation -> {
            AdminKnowledge inserted = invocation.getArgument(0);
            return 1;
        }).when(knowledgeMapper).insertIfContentSha256Absent(any(AdminKnowledge.class));
        when(outboxEventWriter.appendEvent(eq("ADMIN_KNOWLEDGE"), anyLong(),
                eq("KNOWLEDGE_INDEX_UPSERT"), anyString())).thenReturn(78L);

        AdminKnowledgeVO result = service.create(99L, request("规则正文", "新标题"));

        assertFalse(result.getDuplicate());
        assertEquals("PENDING", result.getIndexStatus());
        assertEquals(1L, result.getIndexRevision());
        assertEquals(78L, result.getIndexTaskId());
        ArgumentCaptor<AdminKnowledge> inserted = ArgumentCaptor.forClass(AdminKnowledge.class);
        verify(knowledgeMapper).insertIfContentSha256Absent(inserted.capture());
        assertTrue(inserted.getValue().getId() > 0);
        assertEquals(AiContentHashSupport.knowledgeBodySha256("规则正文"), inserted.getValue().getContentSha256());
        verify(outboxEventWriter).appendEvent(eq("ADMIN_KNOWLEDGE"), eq(inserted.getValue().getId()),
                eq("KNOWLEDGE_INDEX_UPSERT"), anyString());
    }

    private static AdminKnowledgeSaveRequestDTO request(String content, String title) {
        AdminKnowledgeSaveRequestDTO request = new AdminKnowledgeSaveRequestDTO();
        request.setTitle(title);
        request.setContent(content);
        return request;
    }

    private static AdminKnowledge document(Long id, int status, String indexStatus, long revision) {
        AdminKnowledge document = new AdminKnowledge();
        document.setId(id);
        document.setTitle("原有标题");
        document.setContent("规则正文");
        document.setContentSha256(AiContentHashSupport.knowledgeBodySha256("规则正文"));
        document.setStatus(status);
        document.setIndexStatus(indexStatus);
        document.setIndexRevision(revision);
        document.setIndexTaskId(8L);
        return document;
    }
}
