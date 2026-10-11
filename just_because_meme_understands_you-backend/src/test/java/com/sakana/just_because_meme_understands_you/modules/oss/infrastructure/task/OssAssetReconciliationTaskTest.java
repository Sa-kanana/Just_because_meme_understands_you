package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.task;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.OSSObjectSummary;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssAssetReconciliationCoordinator;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssCleanupProperties;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileOrphanCandidateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 中文注释：确认孤儿对账只扫描允许的前缀、分页读取并将对象交给安全事务协调器。 */
@ExtendWith(MockitoExtension.class)
class OssAssetReconciliationTaskTest {

    /** 中文注释：模拟OSS分页列举接口。 */
    @Mock private OSS ossClient;
    /** 中文注释：模拟每个对象的安全状态推进。 */
    @Mock private OssAssetReconciliationCoordinator coordinator;
    /** 中文注释：模拟资产状态汇总。 */
    @Mock private FileAssetMapper fileAssetMapper;
    /** 中文注释：模拟孤儿候选汇总。 */
    @Mock private FileOrphanCandidateMapper candidateMapper;
    /** 中文注释：被测的每日孤儿对账任务。 */
    @InjectMocks private OssAssetReconciliationTask task;

    @Test
    void scansAllowedPrefixAcrossPagesAndSkipsProtectedPrefix() {
        OssCleanupProperties properties = new OssCleanupProperties();
        properties.setReconcileEnabled(true);
        properties.setReconcilePrefixes(List.of("memes/", "system/"));
        ReflectionTestUtils.setField(task, "cleanupProperties", properties);
        ReflectionTestUtils.setField(task, "bucketName", "test-bucket");

        ObjectListing first = mock(ObjectListing.class);
        ObjectListing second = mock(ObjectListing.class);
        when(first.getObjectSummaries()).thenReturn(List.of(summary("memes/a.png")));
        when(first.isTruncated()).thenReturn(true);
        when(first.getNextMarker()).thenReturn("memes/a.png");
        when(second.getObjectSummaries()).thenReturn(List.of(summary("memes/b.png")));
        when(second.isTruncated()).thenReturn(false);
        when(ossClient.listObjects(any(ListObjectsRequest.class))).thenReturn(first, second);
        when(coordinator.observe(any(), any()))
                .thenReturn(OssAssetReconciliationCoordinator.Observation.DISCOVERED);

        OssAssetReconciliationTask.ReconciliationSummary result = task.reconcile();

        assertThat(result.scanned()).isEqualTo(2);
        ArgumentCaptor<ListObjectsRequest> requests = ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(ossClient, times(2)).listObjects(requests.capture());
        assertThat(requests.getAllValues()).extracting(ListObjectsRequest::getPrefix)
                .containsExactly("memes/", "memes/");
        assertThat(requests.getAllValues().get(1).getMarker()).isEqualTo("memes/a.png");
        verify(coordinator).observe(eq("memes/a.png"), any());
        verify(coordinator).observe(eq("memes/b.png"), any());
        verify(coordinator, never()).observe(eq("system/a.png"), any());
    }

    private OSSObjectSummary summary(String key) {
        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setKey(key);
        summary.setLastModified(new Date(System.currentTimeMillis() - 10L * 24 * 3600 * 1000));
        return summary;
    }
}
