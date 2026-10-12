package com.sakana.just_because_meme_understands_you.modules.ai.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.MemeVectorReconciliationJobMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MemeVectorReconciliationServiceTest {

    private final MemeVectorReconciliationJobMapper jobMapper = mock(MemeVectorReconciliationJobMapper.class);
    private final MemeVectorReconciliationService service = new MemeVectorReconciliationService(jobMapper);

    @Test
    void requiredReconciliationTypesArePresent() throws Exception {
        assertEquals(MemeVectorReconciliationJob.class,
                Class.forName("com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob"));
        assertEquals(MemeVectorReconciliationJobMapper.class,
                Class.forName("com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.MemeVectorReconciliationJobMapper"));
    }

    @Test
    void startCreatesAnActiveTaskWhenNoTaskExists() {
        MemeVectorReconciliationJob created = job(41L, "PENDING", "SYNC_PUBLISHED");
        when(jobMapper.insertActiveJob(7L)).thenReturn(1);
        when(jobMapper.selectActiveJobForUpdate()).thenReturn(created);

        var result = service.start(7L);

        assertEquals(41L, result.getJobId());
        assertEquals("PENDING", result.getStatus());
        verify(jobMapper).insertActiveJob(7L);
    }

    @Test
    void startReturnsExistingActiveTaskWithoutCreatingAnother() {
        MemeVectorReconciliationJob active = job(42L, "RUNNING", "SYNC_PUBLISHED");
        when(jobMapper.insertActiveJob(7L)).thenReturn(0);
        when(jobMapper.selectActiveJobForUpdate()).thenReturn(active);

        var result = service.start(7L);

        assertEquals(42L, result.getJobId());
        assertEquals("RUNNING", result.getStatus());
        verify(jobMapper).insertActiveJob(7L);
    }

    @Test
    void findReturnsOnlyTheRequestedJobAndMapsQueuedCounts() {
        MemeVectorReconciliationJob job = job(43L, "COMPLETED", "CLEAN_ORPHAN");
        job.setUpsertQueued(4L);
        job.setRemovalQueued(2L);
        when(jobMapper.selectById(43L)).thenReturn(job);

        var result = service.find(43L);

        assertEquals(43L, result.getJobId());
        assertEquals(4L, result.getUpsertQueued());
        assertEquals(2L, result.getRemovalQueued());
    }

    @Test
    void resumeRequiresFailedTaskAndReservesTheActiveSlot() {
        MemeVectorReconciliationJob failed = job(44L, "FAILED", "CLEAN_ORPHAN");
        when(jobMapper.selectByIdForUpdate(44L)).thenReturn(failed);
        when(jobMapper.selectActiveJobForUpdate()).thenReturn(null);
        when(jobMapper.resumeFailed(44L)).thenReturn(1);
        when(jobMapper.selectById(44L)).thenReturn(job(44L, "PENDING", "CLEAN_ORPHAN"));

        var result = service.resume(44L, 9L);

        assertEquals("PENDING", result.getStatus());
        verify(jobMapper).resumeFailed(44L);
    }

    @Test
    void resumeRejectsNonFailedTask() {
        when(jobMapper.selectByIdForUpdate(45L)).thenReturn(job(45L, "RUNNING", "SYNC_PUBLISHED"));

        assertThrows(BizException.class, () -> service.resume(45L, 9L));
        verify(jobMapper, never()).resumeFailed(anyLong());
    }

    private MemeVectorReconciliationJob job(long id, String status, String phase) {
        MemeVectorReconciliationJob job = new MemeVectorReconciliationJob();
        job.setId(id);
        job.setStatus(status);
        job.setPhase(phase);
        job.setPublishedScanned(0L);
        job.setUpsertQueued(0L);
        job.setVectorScanned(0L);
        job.setRemovalQueued(0L);
        return job;
    }
}
