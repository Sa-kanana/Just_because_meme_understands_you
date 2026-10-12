package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.task.TempFileAssetCleanupTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TempFileAssetCleanupTaskTest {

    @Mock
    private FileAssetMapper fileAssetMapper;

    @Mock
    private FileAssetDeleteTaskCoordinator taskCoordinator;

    @Mock
    private OSS ossClient;

    private TempFileAssetCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        cleanupTask = new TempFileAssetCleanupTask();
        OssCleanupProperties properties = new OssCleanupProperties();
        properties.setBatchSize(2);
        ReflectionTestUtils.setField(cleanupTask, "cleanupProperties", properties);
        ReflectionTestUtils.setField(cleanupTask, "fileAssetMapper", fileAssetMapper);
        ReflectionTestUtils.setField(cleanupTask, "taskCoordinator", taskCoordinator);
        ReflectionTestUtils.setField(cleanupTask, "ossClient", ossClient);
        ReflectionTestUtils.setField(cleanupTask, "bucketName", "test-bucket");
    }

    @Test
    void queuesExistingExpiredTempObjectAndKeepsCutoffForTransactionalRecheck() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(5);
        FileAsset asset = asset(51L, "tmp/meme/51/image.webp");
        when(fileAssetMapper.selectExpiredTemporaryAssets(cutoff, null, 2)).thenReturn(List.of(asset));
        when(ossClient.doesObjectExist("test-bucket", asset.getObjectKey())).thenReturn(true);
        when(taskCoordinator.expireTemporaryAsset(asset.getId(), cutoff, true)).thenReturn(true);

        int processed = cleanupTask.expireUnconfirmedAssets(cutoff);

        assertEquals(1, processed);
        verify(taskCoordinator).expireTemporaryAsset(asset.getId(), cutoff, true);
    }

    @Test
    void preservesTempRecordAndRetriesLaterWhenOssCannotBeChecked() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(5);
        FileAsset asset = asset(52L, "tmp/comment/52/image.webp");
        when(fileAssetMapper.selectExpiredTemporaryAssets(cutoff, null, 2)).thenReturn(List.of(asset));
        when(ossClient.doesObjectExist("test-bucket", asset.getObjectKey()))
                .thenThrow(new IllegalStateException("OSS unavailable"));

        int processed = cleanupTask.expireUnconfirmedAssets(cutoff);

        assertEquals(0, processed);
        verify(taskCoordinator, never()).expireTemporaryAsset(any(), any(), any(Boolean.class));
    }

    private FileAsset asset(Long id, String objectKey) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setObjectKey(objectKey);
        asset.setStatus("TEMP");
        return asset;
    }
}
