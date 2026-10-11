package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileOrphanCandidate;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetBackfillStateMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileOrphanCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 中文注释：验证孤儿候选观察期、默认dry-run、回填门禁和入队幂等。 */
@ExtendWith(MockitoExtension.class)
class OssAssetReconciliationCoordinatorTest {

    /** 中文注释：模拟资产记录。 */
    @Mock private FileAssetMapper assetMapper;
    /** 中文注释：模拟孤儿候选记录。 */
    @Mock private FileOrphanCandidateMapper candidateMapper;
    /** 中文注释：模拟可靠删除任务。 */
    @Mock private FileAssetDeleteTaskMapper taskMapper;
    /** 中文注释：模拟历史回填门禁。 */
    @Mock private FileAssetBackfillStateMapper backfillMapper;
    /** 中文注释：被测孤儿对账事务协调器。 */
    @InjectMocks private OssAssetReconciliationCoordinator coordinator;

    /** 中文注释：每个测试启用扫描，但保持生产默认的dry-run设置。 */
    @BeforeEach
    void setUp() {
        OssCleanupProperties properties = new OssCleanupProperties();
        properties.setReconcileEnabled(true);
        ReflectionTestUtils.setField(coordinator, "cleanupProperties", properties);
    }

    @Test
    void firstDiscoveryOnlyPersistsCandidate() {
        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.DISCOVERED);
        verify(candidateMapper).insert(argThat((FileOrphanCandidate candidate) -> "DISCOVERED".equals(candidate.getStatus())));
        verifyNoInteractions(taskMapper, backfillMapper);
    }

    @Test
    void defaultDryRunConfirmsOldCandidateWithoutCreatingTask() {
        FileOrphanCandidate candidate = oldCandidate();
        when(candidateMapper.selectByObjectKeyForUpdate(anyString())).thenReturn(candidate);

        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.CONFIRMED_DRY_RUN);
        assertThat(candidate.getConfirmedAt()).isNotNull();
        assertThat(candidate.getStatus()).isEqualTo("DISCOVERED");
        verifyNoInteractions(taskMapper, backfillMapper);
    }

    @Test
    void objectTooNewStaysDiscoveredEvenAfterCandidateAges() {
        when(candidateMapper.selectByObjectKeyForUpdate(anyString())).thenReturn(oldCandidate());

        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(1)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.DISCOVERED);
        verifyNoInteractions(taskMapper);
    }

    @Test
    void liveModeQueuesOnceOnlyAfterBackfillCompletes() {
        OssCleanupProperties properties = (OssCleanupProperties) ReflectionTestUtils.getField(coordinator, "cleanupProperties");
        properties.setReconcileDryRun(false);
        FileOrphanCandidate candidate = oldCandidate();
        when(candidateMapper.selectByObjectKeyForUpdate(anyString())).thenReturn(candidate);
        when(backfillMapper.countAllStates(anyString())).thenReturn(7L);
        when(backfillMapper.countIncompleteStates(anyString())).thenReturn(0L);
        when(assetMapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(123L);
            return 1;
        });
        when(taskMapper.insert(any(FileAssetDeleteTask.class))).thenReturn(1);

        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.QUEUED);
        assertThat(candidate.getStatus()).isEqualTo("QUEUED");
        verify(taskMapper).insert(argThat((FileAssetDeleteTask task) -> task.getAssetId() == 123L
                && "ORPHAN_RECONCILIATION".equals(task.getReason())));
        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.SKIPPED);
        verify(taskMapper, times(1)).insert(any(FileAssetDeleteTask.class));
    }

    @Test
    void backfillIncompleteNeverQueuesAndRegisteredAssetIsProtected() {
        OssCleanupProperties properties = (OssCleanupProperties) ReflectionTestUtils.getField(coordinator, "cleanupProperties");
        properties.setReconcileDryRun(false);
        FileOrphanCandidate candidate = oldCandidate();
        when(candidateMapper.selectByObjectKeyForUpdate(anyString())).thenReturn(candidate);

        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.SKIPPED);
        verifyNoInteractions(taskMapper);

        when(assetMapper.selectByObjectKeyForUpdate(anyString())).thenReturn(new FileAsset());
        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.REFERENCED);
        assertThat(candidate.getStatus()).isEqualTo("REFERENCED");
        verifyNoInteractions(taskMapper);
    }

    @Test
    void unsafeKeysAreNeverRecorded() {
        assertThat(coordinator.observe("system/key.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.SKIPPED);
        assertThat(coordinator.observe("memes/../key.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.SKIPPED);
        assertThat(coordinator.observe("common/shared.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.SKIPPED);
        assertThat(coordinator.observe("home/banner.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.SKIPPED);
        verifyNoInteractions(candidateMapper, assetMapper, taskMapper);
    }

    @Test
    void previouslyReferencedObjectStartsFreshObservationIfRegistrationDisappears() {
        OssCleanupProperties properties = (OssCleanupProperties) ReflectionTestUtils.getField(coordinator, "cleanupProperties");
        properties.setReconcileDryRun(false);
        FileOrphanCandidate candidate = oldCandidate();
        candidate.setStatus("REFERENCED");
        when(candidateMapper.selectByObjectKeyForUpdate(anyString())).thenReturn(candidate);

        assertThat(coordinator.observe("memes/unknown.png", LocalDateTime.now().minusDays(10)))
                .isEqualTo(OssAssetReconciliationCoordinator.Observation.DISCOVERED);
        assertThat(candidate.getStatus()).isEqualTo("DISCOVERED");
        assertThat(candidate.getFirstSeenAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        verifyNoInteractions(taskMapper, backfillMapper);
    }

    private FileOrphanCandidate oldCandidate() {
        FileOrphanCandidate candidate = new FileOrphanCandidate();
        candidate.setId(1L);
        candidate.setObjectKey("memes/unknown.png");
        candidate.setStatus("DISCOVERED");
        candidate.setFirstSeenAt(LocalDateTime.now().minusDays(8));
        return candidate;
    }
}
