package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetBackfillState;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetReference;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetBackfillStateMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetLegacyReferenceMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.LegacyFileAssetReferenceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAssetLegacyBackfillRunnerTest {

    @Mock
    private FileAssetLegacyReferenceMapper legacyReferenceMapper;

    @Mock
    private FileAssetBackfillStateMapper backfillStateMapper;

    @Mock
    private FileAssetMapper fileAssetMapper;

    @Mock
    private FileAssetReferenceMapper fileAssetReferenceMapper;

    private FileAssetLegacyBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new FileAssetLegacyBackfillRunner();
        OssCleanupProperties properties = new OssCleanupProperties();
        properties.setBackfillBatchSize(20);
        ReflectionTestUtils.setField(runner, "cleanupProperties", properties);
        ReflectionTestUtils.setField(runner, "legacyReferenceMapper", legacyReferenceMapper);
        ReflectionTestUtils.setField(runner, "backfillStateMapper", backfillStateMapper);
        ReflectionTestUtils.setField(runner, "fileAssetMapper", fileAssetMapper);
        ReflectionTestUtils.setField(runner, "fileAssetReferenceMapper", fileAssetReferenceMapper);
        ReflectionTestUtils.setField(runner, "ossUrlHelper", configuredUrlHelper());
        ReflectionTestUtils.setField(runner, "objectMapper", new ObjectMapper());
    }

    @Test
    void deduplicatesRepeatedObjectKeysAndCreatesEachBusinessReference() {
        prepareLease("USER_AVATAR");
        when(legacyReferenceMapper.selectUserAvatarBatch(null, 20)).thenReturn(List.of(
                row(11L, 11L, "https://cdn.example.test/avatar/shared.png"),
                row(12L, 12L, "avatar/shared.png")));
        when(fileAssetMapper.selectByObjectKey("avatar/shared.png")).thenReturn(activeAsset(81L));

        runner.runNextBatch();

        verify(fileAssetMapper).insertLegacyAssetIfAbsent("avatar/shared.png", "avatar");
        ArgumentCaptor<FileAssetReference> captor = ArgumentCaptor.forClass(FileAssetReference.class);
        verify(fileAssetReferenceMapper, org.mockito.Mockito.times(2))
                .insertIgnoreLegacyReference(captor.capture());
        assertEquals(List.of(11L, 12L), captor.getAllValues().stream().map(FileAssetReference::getBizId).toList());
        assertEquals(List.of("USER", "USER"), captor.getAllValues().stream().map(FileAssetReference::getBizType).toList());
    }

    @Test
    void skipsExternalUnconfiguredProtectedAndUnsafeKeysButStillAdvancesCheckpoint() {
        prepareLease("USER_AVATAR");
        when(legacyReferenceMapper.selectUserAvatarBatch(null, 20)).thenReturn(List.of(
                row(21L, 21L, "https://images.example.org/avatar/a.png"),
                row(22L, 22L, "https://another-bucket.oss-cn-hangzhou.aliyuncs.com/avatar/b.png"),
                row(23L, 23L, "https://cdn.example.test/system/logo.png"),
                row(24L, 24L, "memes/../avatar/escape.png")));

        runner.runNextBatch();

        verify(fileAssetMapper, never()).insertLegacyAssetIfAbsent(anyString(), anyString());
        verify(fileAssetReferenceMapper, never()).insertIgnoreLegacyReference(any());
        verify(backfillStateMapper).markSourceProgress(
                FileAssetLegacyBackfillRunner.BACKFILL_KEY, "USER_AVATAR", 24L, "COMPLETED");
    }

    @Test
    void createsCoverAndIndexedResourceReferencesForPendingRevision() {
        prepareLease("MEME_REVISION");
        when(legacyReferenceMapper.selectMemeRevisionBatch(null, 20)).thenReturn(List.of(
                revisionRow(31L, "{\"image\":\"https://cdn.example.test/memes/cover.png\","
                        + "\"resourceUrls\":[\"https://bucket.oss-cn-hangzhou.aliyuncs.com/comments/a.png\","
                        + "\"https://remote.example.org/video\"]}")));
        when(fileAssetMapper.selectByObjectKey("memes/cover.png")).thenReturn(activeAsset(91L));
        when(fileAssetMapper.selectByObjectKey("comments/a.png")).thenReturn(activeAsset(92L));

        runner.runNextBatch();

        ArgumentCaptor<FileAssetReference> captor = ArgumentCaptor.forClass(FileAssetReference.class);
        verify(fileAssetReferenceMapper, org.mockito.Mockito.times(2))
                .insertIgnoreLegacyReference(captor.capture());
        assertEquals(List.of("COVER", "RESOURCE:0"),
                captor.getAllValues().stream().map(FileAssetReference::getFieldKey).toList());
        assertEquals(List.of(91L, 92L), captor.getAllValues().stream().map(FileAssetReference::getAssetId).toList());
    }

    @Test
    void doesNotReadSourcesWhenAnotherInstanceOwnsTheLease() {
        when(backfillStateMapper.ensureStateRows(anyString(), anyList())).thenReturn(7);
        when(backfillStateMapper.claimRunnerLease(anyString(), anyString(), any()))
                .thenReturn(0);

        runner.runNextBatch();

        verify(legacyReferenceMapper, never()).selectUserAvatarBatch(any(), anyInt());
        verify(backfillStateMapper, never()).markSourceProgress(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void savesFullBatchCursorAndResumesFromThatCursorUntilTheSourceIsExhausted() {
        FileAssetBackfillState firstPageState = new FileAssetBackfillState();
        firstPageState.setSourceName("USER_AVATAR");
        firstPageState.setStatus("PENDING");
        FileAssetBackfillState resumedState = new FileAssetBackfillState();
        resumedState.setSourceName("USER_AVATAR");
        resumedState.setStatus("PENDING");
        resumedState.setLastSourceId(20L);
        when(backfillStateMapper.ensureStateRows(anyString(), anyList())).thenReturn(7);
        when(backfillStateMapper.claimRunnerLease(anyString(), anyString(), any())).thenReturn(1);
        when(backfillStateMapper.selectNextIncompleteSource(FileAssetLegacyBackfillRunner.BACKFILL_KEY))
                .thenReturn(firstPageState, resumedState);
        when(backfillStateMapper.countIncompleteSourceStates(FileAssetLegacyBackfillRunner.BACKFILL_KEY))
                .thenReturn(5L, 0L);
        List<LegacyFileAssetReferenceRow> fullBatch = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(id -> row(id, id, "avatar/shared.png")).toList();
        when(legacyReferenceMapper.selectUserAvatarBatch(null, 20)).thenReturn(fullBatch);
        when(legacyReferenceMapper.selectUserAvatarBatch(20L, 20)).thenReturn(List.of());
        when(fileAssetMapper.selectByObjectKey("avatar/shared.png")).thenReturn(activeAsset(81L));

        assertEquals(20, runner.runNextBatch());
        assertEquals(0, runner.runNextBatch());

        verify(backfillStateMapper).markSourceProgress(
                FileAssetLegacyBackfillRunner.BACKFILL_KEY, "USER_AVATAR", 20L, "PENDING");
        verify(backfillStateMapper).markSourceProgress(
                FileAssetLegacyBackfillRunner.BACKFILL_KEY, "USER_AVATAR", 20L, "COMPLETED");
        verify(legacyReferenceMapper).selectUserAvatarBatch(20L, 20);
    }

    private void prepareLease(String sourceName) {
        when(backfillStateMapper.ensureStateRows(anyString(), anyList())).thenReturn(7);
        when(backfillStateMapper.claimRunnerLease(anyString(), anyString(), any())).thenReturn(1);
        FileAssetBackfillState state = new FileAssetBackfillState();
        state.setSourceName(sourceName);
        state.setStatus("PENDING");
        when(backfillStateMapper.selectNextIncompleteSource(FileAssetLegacyBackfillRunner.BACKFILL_KEY))
                .thenReturn(state);
        when(backfillStateMapper.countIncompleteSourceStates(FileAssetLegacyBackfillRunner.BACKFILL_KEY))
                .thenReturn(0L);
    }

    private OssUrlHelper configuredUrlHelper() {
        OssUrlHelper helper = new OssUrlHelper();
        ReflectionTestUtils.setField(helper, "bucketName", "bucket");
        ReflectionTestUtils.setField(helper, "endpoint", "oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(helper, "uploadHost", "https://bucket.oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(helper, "publicBaseUrl", "https://cdn.example.test");
        ReflectionTestUtils.invokeMethod(helper, "init");
        return helper;
    }

    private LegacyFileAssetReferenceRow row(Long sourceId, Long bizId, String value) {
        LegacyFileAssetReferenceRow row = new LegacyFileAssetReferenceRow();
        row.setSourceId(sourceId);
        row.setBizId(bizId);
        row.setObjectValue(value);
        return row;
    }

    private LegacyFileAssetReferenceRow revisionRow(Long revisionId, String contentJson) {
        LegacyFileAssetReferenceRow row = row(revisionId, revisionId, null);
        row.setContentJson(contentJson);
        return row;
    }

    private FileAsset activeAsset(Long id) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setStatus("ACTIVE");
        return asset;
    }
}
