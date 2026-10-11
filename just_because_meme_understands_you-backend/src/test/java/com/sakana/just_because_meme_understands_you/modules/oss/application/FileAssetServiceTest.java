package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.transaction.AfterCommitExecutor;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetReference;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** 中文注释：验证文件资产绑定、替换、释放和安全 promote 规则。 */
class FileAssetServiceTest {

    private static final String SERVICE_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService";
    private static final String DELETE_TASK_MAPPER_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper";

    @Test
    void rejectsUnconfirmedTemporaryAssetBeforePromoting() throws Throwable {
        FileAsset asset = temporaryAsset(7L, "meme", null);
        FileAssetMapper fileMapper = fileMapper(List.of(asset));
        OssObjectPromoteService promoteService = mock(OssObjectPromoteService.class);
        Object service = createService(fileMapper, referenceMapper(List.of(), 0), newDeleteTaskMapper(new ArrayList<>()), promoteService);

        assertThrows(BizException.class, () -> invoke(service, "promote",
                new Class<?>[]{String.class, Long.class, String.class}, asset.getObjectKey(), 7L, "meme"));

        verify(promoteService, never()).promoteMemeImage(asset.getObjectKey(), 7L);
    }

    @Test
    void rejectsTemporaryAssetOwnedByAnotherUploader() throws Throwable {
        FileAsset asset = temporaryAsset(8L, "meme", LocalDateTime.now());
        Object service = createService(fileMapper(List.of(asset)), referenceMapper(List.of(), 0),
                newDeleteTaskMapper(new ArrayList<>()), mock(OssObjectPromoteService.class));

        assertThrows(BizException.class, () -> invoke(service, "promote",
                new Class<?>[]{String.class, Long.class, String.class}, asset.getObjectKey(), 99L, "meme"));
    }

    @Test
    void promotesConfirmedTemporaryAssetAndUpdatesItsLifecycleState() throws Throwable {
        FileAsset asset = temporaryAsset(9L, "meme", LocalDateTime.now());
        OssObjectPromoteService promoteService = mock(OssObjectPromoteService.class);
        when(promoteService.promoteMemeImage(asset.getObjectKey(), 7L))
                .thenReturn("memes/20261011/promoted.png");
        Object service = createService(fileMapper(List.of(asset)), referenceMapper(List.of(), 0),
                newDeleteTaskMapper(new ArrayList<>()), promoteService);

        String promotedKey = (String) invoke(service, "promote",
                new Class<?>[]{String.class, Long.class, String.class}, asset.getObjectKey(), 7L, "meme");

        assertEquals("memes/20261011/promoted.png", promotedKey);
        assertEquals("ACTIVE", asset.getStatus());
        assertNotNull(asset.getActivatedAt());
    }

    @Test
    void promotesNewSingularMemeAndCommentTicketPrefixes() {
        OSS oss = mock(OSS.class);
        when(oss.doesObjectExist("test-bucket", "tmp/meme/7/20261011/meme.png")).thenReturn(true);
        when(oss.doesObjectExist("test-bucket", "tmp/comment/7/20261011/comment.png")).thenReturn(true);
        OssUrlHelper urlHelper = mock(OssUrlHelper.class);
        when(urlHelper.normalizeForStorage(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        OssObjectPromoteService promoteService = new OssObjectPromoteService();
        ReflectionTestUtils.setField(promoteService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(promoteService, "ossClient", oss);
        ReflectionTestUtils.setField(promoteService, "ossUrlHelper", urlHelper);
        ReflectionTestUtils.setField(promoteService, "afterCommitExecutor", mock(AfterCommitExecutor.class));

        String memeKey = promoteService.promoteMemeImage("tmp/meme/7/20261011/meme.png", 7L);
        String commentKey = promoteService.promoteCommentImage("tmp/comment/7/20261011/comment.png", 7L);

        assertTrue(memeKey.startsWith("memes/"));
        assertTrue(commentKey.startsWith("comments/"));
    }

    @Test
    void keepsSharedAssetActiveWhenOneReferenceIsReleased() throws Throwable {
        FileAsset asset = activeAsset(10L, "memes/20261011/shared.png");
        FileAssetReference removedReference = reference(101L, 10L, "MEME", 20L, "COVER");
        FileAssetMapper fileMapper = fileMapper(List.of(asset));
        List<Object> createdTasks = new ArrayList<>();
        Object service = createService(fileMapper, referenceMapper(List.of(removedReference), 1),
                newDeleteTaskMapper(createdTasks), mock(OssObjectPromoteService.class));

        invoke(service, "releaseReference",
                new Class<?>[]{String.class, Long.class, String.class, String.class},
                "MEME", 20L, "COVER", "MEME_DELETED");

        assertEquals("ACTIVE", asset.getStatus(), "仍有引用的共享资产不能进入待删除状态");
        assertTrue(createdTasks.isEmpty(), "仍有引用时不得创建 OSS 删除任务");
    }

    @Test
    void queuesOnlyLastReferenceForDelayedDeletion() throws Throwable {
        FileAsset asset = activeAsset(11L, "memes/20261011/unused.png");
        FileAssetReference lastReference = reference(102L, 11L, "MEME", 21L, "COVER");
        FileAssetMapper fileMapper = fileMapper(List.of(asset));
        List<Object> createdTasks = new ArrayList<>();
        Object service = createService(fileMapper, referenceMapper(List.of(lastReference), 0),
                newDeleteTaskMapper(createdTasks), mock(OssObjectPromoteService.class));

        invoke(service, "releaseReference",
                new Class<?>[]{String.class, Long.class, String.class, String.class},
                "MEME", 21L, "COVER", "MEME_DELETED");

        assertEquals("PENDING_DELETE", asset.getStatus());
        assertNotNull(asset.getDeleteAfter(), "删除前必须保留缓冲期");
        assertEquals(1, createdTasks.size(), "最后一个引用释放后创建一条持久化删除任务");
        assertEquals("WAITING", ReflectionTestUtils.getField(createdTasks.get(0), "status"));
    }

    @Test
    void restoresPendingAssetAndCancelsWaitingTaskWhenRebound() throws Throwable {
        FileAsset asset = activeAsset(12L, "memes/20261011/reused.png");
        asset.setStatus("PENDING_DELETE");
        asset.setDeleteAfter(LocalDateTime.now().plusHours(1));
        FileAssetMapper fileMapper = fileMapper(List.of(asset));
        AtomicInteger canceledTaskCount = new AtomicInteger();
        Object taskMapper = newDeleteTaskMapper(new ArrayList<>(), canceledTaskCount);
        Object service = createService(fileMapper, referenceMapper(List.of(), 0), taskMapper,
                mock(OssObjectPromoteService.class));

        invoke(service, "replaceReference",
                new Class<?>[]{String.class, String.class, Long.class, String.class, String.class},
                asset.getObjectKey(), "USER", 22L, "AVATAR", "AVATAR_REPLACED");

        assertEquals("ACTIVE", asset.getStatus());
        assertEquals(null, asset.getDeleteAfter());
        assertEquals(1, canceledTaskCount.get(), "重新绑定必须取消仍可撤销的删除任务");
    }

    @Test
    void refusesToRebindAssetAlreadyDeletingOrDeleted() throws Throwable {
        for (String status : List.of("DELETING", "DELETED")) {
            FileAsset asset = activeAsset(13L, "memes/20261011/closed.png");
            asset.setStatus(status);
            Object service = createService(fileMapper(List.of(asset)), referenceMapper(List.of(), 0),
                    newDeleteTaskMapper(new ArrayList<>()), mock(OssObjectPromoteService.class));

            assertThrows(BizException.class, () -> invoke(service, "replaceReference",
                    new Class<?>[]{String.class, String.class, Long.class, String.class, String.class},
                    asset.getObjectKey(), "USER", 23L, "AVATAR", "AVATAR_REPLACED"), status);
        }
    }

    private Object createService(FileAssetMapper fileMapper, FileAssetReferenceMapper referenceMapper,
                                 Object taskMapper, OssObjectPromoteService promoteService) throws Exception {
        Object service = Class.forName(SERVICE_CLASS).getDeclaredConstructor().newInstance();
        setFieldIfPresent(service, "fileAssetMapper", fileMapper);
        setFieldIfPresent(service, "fileAssetReferenceMapper", referenceMapper);
        setFieldIfPresent(service, "fileAssetDeleteTaskMapper", taskMapper);
        setFieldIfPresent(service, "ossObjectPromoteService", promoteService);
        OssUrlHelper urlHelper = mock(OssUrlHelper.class);
        org.mockito.Mockito.when(urlHelper.normalizeForStorage(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        setFieldIfPresent(service, "ossUrlHelper", urlHelper);
        setFieldIfPresent(service, "cleanupProperties", new OssCleanupProperties());
        return service;
    }

    private FileAssetMapper fileMapper(List<FileAsset> assets) {
        Map<Long, FileAsset> byId = new HashMap<>();
        Map<String, FileAsset> byKey = new HashMap<>();
        assets.forEach(asset -> {
            byId.put(asset.getId(), asset);
            byKey.put(asset.getObjectKey(), asset);
        });
        return mock(FileAssetMapper.class, invocation -> switch (invocation.getMethod().getName()) {
            case "selectById", "selectByIdForUpdate" -> byId.get((Long) invocation.getArgument(0));
            case "selectByObjectKey", "selectByObjectKeyForUpdate" -> byKey.get((String) invocation.getArgument(0));
            case "updateById" -> 1;
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private FileAssetReferenceMapper referenceMapper(List<FileAssetReference> currentReferences, long remainingCount) {
        return mock(FileAssetReferenceMapper.class, invocation -> switch (invocation.getMethod().getName()) {
            case "selectByBusinessField", "selectByBusinessFieldForUpdate" -> currentReferences;
            case "countByAssetId" -> remainingCount;
            case "insert", "deleteById", "delete" -> 1;
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object newDeleteTaskMapper(List<Object> insertedTasks) throws Exception {
        return newDeleteTaskMapper(insertedTasks, new AtomicInteger());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object newDeleteTaskMapper(List<Object> insertedTasks, AtomicInteger canceledTaskCount) throws Exception {
        Class<?> mapperType = Class.forName(DELETE_TASK_MAPPER_CLASS);
        return mock((Class) mapperType, invocation -> switch (invocation.getMethod().getName()) {
            case "insert" -> {
                insertedTasks.add(invocation.getArgument(0));
                yield 1;
            }
            case "cancelRebindableTasks" -> {
                canceledTaskCount.incrementAndGet();
                yield 1;
            }
            case "updateById" -> 1;
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private FileAsset temporaryAsset(Long id, String scene, LocalDateTime uploadedAt) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setObjectKey("tmp/" + scene + "/7/20261011/asset.png");
        asset.setOwnerId(7L);
        asset.setScene(scene);
        asset.setStatus("TEMP");
        asset.setUploadedAt(uploadedAt);
        asset.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        return asset;
    }

    private FileAsset activeAsset(Long id, String objectKey) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setObjectKey(objectKey);
        asset.setOwnerId(7L);
        asset.setScene("meme");
        asset.setStatus("ACTIVE");
        asset.setUploadedAt(LocalDateTime.now().minusMinutes(1));
        asset.setCreatedAt(LocalDateTime.now().minusDays(1));
        asset.setActivatedAt(LocalDateTime.now().minusHours(1));
        return asset;
    }

    private FileAssetReference reference(Long id, Long assetId, String bizType, Long bizId, String fieldKey) {
        FileAssetReference reference = new FileAssetReference();
        reference.setId(id);
        reference.setAssetId(assetId);
        reference.setBizType(bizType);
        reference.setBizId(bizId);
        reference.setFieldKey(fieldKey);
        return reference;
    }

    private Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments) throws Throwable {
        Method method = target.getClass().getMethod(name, parameterTypes);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private void setFieldIfPresent(Object target, String fieldName, Object value) {
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName) != null) {
            ReflectionTestUtils.setField(target, fieldName, value);
        }
    }
}
