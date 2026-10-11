package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredTempAssetTransitionTest {

    @Mock
    private FileAssetMapper fileAssetMapper;

    @Mock
    private FileAssetReferenceMapper fileAssetReferenceMapper;

    @Mock
    private FileAssetDeleteTaskMapper fileAssetDeleteTaskMapper;

    private FileAssetDeleteTaskCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new FileAssetDeleteTaskCoordinator();
        ReflectionTestUtils.setField(coordinator, "fileAssetMapper", fileAssetMapper);
        ReflectionTestUtils.setField(coordinator, "fileAssetReferenceMapper", fileAssetReferenceMapper);
        ReflectionTestUtils.setField(coordinator, "fileAssetDeleteTaskMapper", fileAssetDeleteTaskMapper);
        ReflectionTestUtils.setField(coordinator, "cleanupProperties", new OssCleanupProperties());
    }

    @Test
    void queuesExpiredObjectForReliableDeletionAndKeepsAssetAuditRow() {
        FileAsset asset = expiredTempAsset(61L);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        when(fileAssetMapper.selectByIdForUpdate(61L)).thenReturn(asset);
        when(fileAssetMapper.updateById(asset)).thenReturn(1);
        when(fileAssetReferenceMapper.countByAssetId(61L)).thenReturn(0L);
        when(fileAssetDeleteTaskMapper.insert(org.mockito.ArgumentMatchers.any(FileAssetDeleteTask.class))).thenReturn(1);

        boolean queued = coordinator.expireTemporaryAsset(61L, cutoff, true);

        assertTrue(queued);
        assertEquals("PENDING_DELETE", asset.getStatus());
        ArgumentCaptor<FileAssetDeleteTask> taskCaptor = ArgumentCaptor.forClass(FileAssetDeleteTask.class);
        verify(fileAssetDeleteTaskMapper).insert(taskCaptor.capture());
        assertEquals("TEMP_ASSET_EXPIRED", taskCaptor.getValue().getReason());
        assertEquals(61L, taskCaptor.getValue().getAssetId());
    }

    @Test
    void marksMissingExpiredObjectDeletedWithoutRemovingAuditRecord() {
        FileAsset asset = expiredTempAsset(62L);
        when(fileAssetMapper.selectByIdForUpdate(62L)).thenReturn(asset);
        when(fileAssetMapper.updateById(asset)).thenReturn(1);
        when(fileAssetReferenceMapper.countByAssetId(62L)).thenReturn(0L);

        boolean recorded = coordinator.expireTemporaryAsset(62L, LocalDateTime.now().minusDays(3), false);

        assertTrue(recorded);
        assertEquals("DELETED", asset.getStatus());
        assertTrue(asset.getDeletedAt() != null);
        verify(fileAssetDeleteTaskMapper, never()).insert(org.mockito.ArgumentMatchers.any(FileAssetDeleteTask.class));
    }

    @Test
    void protectsAssetIfAReferenceExists() {
        FileAsset asset = expiredTempAsset(63L);
        when(fileAssetMapper.selectByIdForUpdate(63L)).thenReturn(asset);
        when(fileAssetMapper.updateById(asset)).thenReturn(1);
        when(fileAssetReferenceMapper.countByAssetId(63L)).thenReturn(1L);

        boolean queued = coordinator.expireTemporaryAsset(63L, LocalDateTime.now().minusDays(3), true);

        assertFalse(queued);
        assertEquals("ACTIVE", asset.getStatus());
        verify(fileAssetDeleteTaskMapper, never()).insert(org.mockito.ArgumentMatchers.any(FileAssetDeleteTask.class));
    }

    private FileAsset expiredTempAsset(Long id) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setObjectKey("tmp/meme/" + id + "/image.webp");
        asset.setStatus("TEMP");
        asset.setCreatedAt(LocalDateTime.now().minusDays(5));
        return asset;
    }
}
