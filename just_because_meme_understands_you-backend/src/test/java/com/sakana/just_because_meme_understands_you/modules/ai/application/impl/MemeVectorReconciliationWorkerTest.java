package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentMemeIdPageDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.MemeVectorReconciliationJobMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemeVectorReconciliationWorkerTest {

    private final MemeVectorReconciliationJobMapper jobMapper = mock(MemeVectorReconciliationJobMapper.class);
    private final MemeCatalogPort catalog = mock(MemeCatalogPort.class);
    private final MemeAgentClient agentClient = mock(MemeAgentClient.class);
    private final MemeIndexEventPort indexEventPort = mock(MemeIndexEventPort.class);
    private final MemeVectorReconciliationWorker worker = new MemeVectorReconciliationWorker(
            jobMapper, catalog, agentClient, indexEventPort, new ImmediateTransactionTemplate());

    @Test
    void publishedPageQueuesUpsertsAndAdvancesItsCursor() {
        MemeVectorReconciliationJob job = job("SYNC_PUBLISHED");
        when(jobMapper.selectNextRunnableForUpdate()).thenReturn(job);
        when(jobMapper.lease(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(catalog.pagePublishedIdsAfterId(10L, 50)).thenReturn(List.of(11L, 12L));
        when(jobMapper.advancePublishedPage(eq(1L), anyString(), eq(12L), eq(2), any(LocalDateTime.class)))
                .thenReturn(1);

        worker.processOnePage();

        verify(indexEventPort).requestUpsert(11L);
        verify(indexEventPort).requestUpsert(12L);
        verify(jobMapper).advancePublishedPage(eq(1L), anyString(), eq(12L), eq(2), any(LocalDateTime.class));
        verify(agentClient, never()).listMemeIdsPage(any(), anyInt());
    }

    @Test
    void emptyPublishedPageMovesTaskIntoOrphanCleanup() {
        MemeVectorReconciliationJob job = job("SYNC_PUBLISHED");
        when(jobMapper.selectNextRunnableForUpdate()).thenReturn(job);
        when(jobMapper.lease(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(catalog.pagePublishedIdsAfterId(10L, 50)).thenReturn(List.of());
        when(jobMapper.moveToOrphanCleanup(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);

        worker.processOnePage();

        verify(jobMapper).moveToOrphanCleanup(eq(1L), anyString(), any(LocalDateTime.class));
        verify(indexEventPort, never()).requestUpsert(anyLong());
    }

    @Test
    void vectorPageQueuesRemovalOnlyForIdsThatAreNotCurrentlyPublished() {
        MemeVectorReconciliationJob job = job("CLEAN_ORPHAN");
        job.setVectorCursor("100");
        when(jobMapper.selectNextRunnableForUpdate()).thenReturn(job);
        when(jobMapper.lease(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        MemeAgentMemeIdPageDTO page = new MemeAgentMemeIdPageDTO();
        page.setMemeIds(List.of("101", "102", "103"));
        page.setNextCursor(null);
        when(agentClient.listMemeIdsPage("100", 500)).thenReturn(Mono.just(page));
        when(catalog.findPublishedByIds(List.of(101L, 102L, 103L))).thenReturn(List.of(
                new MemeCatalogPort.MemeCard(102L, 7L, "still-live", "", "", 0, 0, 0,
                        1, null, null, 0)));
        when(jobMapper.completeVectorPage(eq(1L), anyString(), eq(3), eq(2))).thenReturn(1);

        worker.processOnePage();

        verify(indexEventPort).requestRemoval(101L);
        verify(indexEventPort, never()).requestRemoval(102L);
        verify(indexEventPort).requestRemoval(103L);
        verify(jobMapper).completeVectorPage(eq(1L), anyString(), eq(3), eq(2));
    }

    @Test
    void externalPageFailureMarksTaskFailedWithoutAdvancingCursor() {
        MemeVectorReconciliationJob job = job("SYNC_PUBLISHED");
        when(jobMapper.selectNextRunnableForUpdate()).thenReturn(job);
        when(jobMapper.lease(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(catalog.pagePublishedIdsAfterId(10L, 50)).thenThrow(new IllegalStateException("db timeout"));
        when(jobMapper.markFailed(eq(1L), anyString(), eq("SYNC_PUBLISHED"))).thenReturn(1);

        worker.processOnePage();

        verify(jobMapper).markFailed(eq(1L), anyString(), eq("SYNC_PUBLISHED"));
        verify(jobMapper, never()).advancePublishedPage(anyLong(), anyString(), anyLong(), anyInt(), any());
        verify(indexEventPort, never()).requestUpsert(anyLong());
    }

    @Test
    void missingRunnableTaskDoesNoCatalogOrVectorScan() {
        when(jobMapper.selectNextRunnableForUpdate()).thenReturn(null);

        worker.processOnePage();

        verifyNoInteractions(catalog, agentClient, indexEventPort);
    }

    private MemeVectorReconciliationJob job(String phase) {
        MemeVectorReconciliationJob job = new MemeVectorReconciliationJob();
        job.setId(1L);
        job.setStatus("PENDING");
        job.setPhase(phase);
        job.setPublishedCursor(10L);
        job.setVectorCursor(null);
        return job;
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
