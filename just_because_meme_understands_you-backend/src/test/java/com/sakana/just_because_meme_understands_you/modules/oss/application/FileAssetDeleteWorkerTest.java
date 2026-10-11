package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetBackfillStateMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：验证 OSS 删除 Worker 的回填门禁、前缀保护、完成与重试行为。 */
class FileAssetDeleteWorkerTest {

    private static final String WORKER_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetDeleteWorker";
    private static final String COORDINATOR_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetDeleteTaskCoordinator";
    private static final String TASK_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask";

    @Test
    void blocksPhysicalDeletionUntilLegacyBackfillIsComplete() throws Throwable {
        AtomicInteger claimCalls = new AtomicInteger();
        Object coordinator = coordinator(List.of(), null, new AtomicInteger(), new AtomicInteger(), claimCalls);
        FileAssetBackfillStateMapper backfillMapper = mock(FileAssetBackfillStateMapper.class,
                invocation -> invocation.getMethod().getName().equals("selectCount")
                        ? (invocation.getArgument(0) == null ? 0L : 0L)
                        : Answers.RETURNS_DEFAULTS.answer(invocation));
        OSS oss = mock(OSS.class);
        Object worker = createWorker(coordinator, backfillMapper, oss);

        int processed = (int) invoke(worker, "processDueTasks", new Class<?>[]{int.class}, 100);

        assertEquals(0, processed);
        assertEquals(0, claimCalls.get(), "历史引用尚未回填时不得领取或物理删除对象");
        verify(oss, never()).deleteObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deletesAllowedUnreferencedObjectAndMarksItsTaskDone() throws Throwable {
        Object task = task(201L, "memes/20261011/unused.png", "instance-a");
        FileAsset asset = asset(301L, "memes/20261011/unused.png");
        AtomicInteger doneCalls = new AtomicInteger();
        Object coordinator = coordinator(List.of(task), asset, doneCalls, new AtomicInteger(), new AtomicInteger());
        FileAssetBackfillStateMapper backfillMapper = completedBackfillMapper();
        OSS oss = mock(OSS.class);
        Object worker = createWorker(coordinator, backfillMapper, oss);

        int processed = (int) invoke(worker, "processDueTasks", new Class<?>[]{int.class}, 100);

        assertEquals(1, processed);
        assertEquals(1, doneCalls.get());
        verify(oss).deleteObject("test-bucket", "memes/20261011/unused.png");
    }

    @Test
    void rejectsProtectedAndPathTraversalKeysBeforeCallingOss() throws Throwable {
        for (String unsafeKey : List.of(
                "system/logo.png", "memes/../system/logo.png", "homepage/static/banner.png", "MEMES/20261011/not-allowed.png")) {
            Object task = task(202L, unsafeKey, "instance-a");
            FileAsset asset = asset(302L, unsafeKey);
            AtomicInteger failedCalls = new AtomicInteger();
            Object coordinator = coordinator(List.of(task), asset, new AtomicInteger(), failedCalls, new AtomicInteger());
            OSS oss = mock(OSS.class);
            Object worker = createWorker(coordinator, completedBackfillMapper(), oss);

            invoke(worker, "processDueTasks", new Class<?>[]{int.class}, 100);

            assertEquals(1, failedCalls.get(), "不安全对象键应记录为终止失败并等待人工处理");
            verify(oss, never()).deleteObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    void recordsRetryWhenOssDeletionFails() throws Throwable {
        Object task = task(203L, "home/20261011/banner.png", "instance-a");
        FileAsset asset = asset(303L, "home/20261011/banner.png");
        AtomicInteger retryCalls = new AtomicInteger();
        Object coordinator = coordinator(List.of(task), asset, new AtomicInteger(), new AtomicInteger(),
                new AtomicInteger(), retryCalls);
        OSS oss = mock(OSS.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("OSS unavailable"))
                .when(oss).deleteObject("test-bucket", asset.getObjectKey());
        Object worker = createWorker(coordinator, completedBackfillMapper(), oss);

        invoke(worker, "processDueTasks", new Class<?>[]{int.class}, 100);

        assertEquals(1, retryCalls.get(), "OSS 删除失败必须重新排队或进入终态，不能静默丢弃");
    }

    @Test
    void usesBoundedBackoffAndStopsAfterFiveRetries() throws Throwable {
        Class<?> coordinatorType = Class.forName(COORDINATOR_CLASS);
        Method retryDelay = coordinatorType.getDeclaredMethod("retryDelayForRetryCount", int.class);
        retryDelay.setAccessible(true);

        assertEquals(Duration.ofMinutes(1), retryDelay.invoke(null, 0));
        assertEquals(Duration.ofMinutes(5), retryDelay.invoke(null, 1));
        assertEquals(Duration.ofMinutes(30), retryDelay.invoke(null, 2));
        assertEquals(Duration.ofHours(2), retryDelay.invoke(null, 3));
        assertEquals(Duration.ofHours(6), retryDelay.invoke(null, 4));
        assertNull(retryDelay.invoke(null, 5), "超过五次重试后不得继续排队");
    }

    @Test
    void claimsOnlyTheConfiguredBatchAndAssignsAnInstanceLease() throws Throwable {
        FileAssetDeleteTask task = typedTask(204L, 304L, "memes/20261011/queued.png", "WAITING", 0);
        FileAssetDeleteTaskMapper taskMapper = mock(FileAssetDeleteTaskMapper.class);
        when(taskMapper.selectDueForUpdate(org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(2))).thenReturn(List.of(task));
        when(taskMapper.updateById(task)).thenReturn(1);
        OssCleanupProperties properties = new OssCleanupProperties();
        properties.setBatchSize(2);
        properties.setWorkerId("instance-a");
        Object coordinator = createCoordinator(mock(FileAssetMapper.class),
                mock(FileAssetReferenceMapper.class), taskMapper, properties);

        @SuppressWarnings("unchecked")
        List<FileAssetDeleteTask> claimed = (List<FileAssetDeleteTask>) invoke(
                coordinator, "claimDueTasks", new Class<?>[]{int.class}, 100);

        assertEquals(1, claimed.size());
        assertEquals("PROCESSING", task.getStatus());
        assertEquals("instance-a", task.getLeaseOwner());
        assertNotNull(task.getLeaseUntil());
        verify(taskMapper).selectDueForUpdate(org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    void cancelsDeleteWhenAReferenceAppearsBeforePhysicalDeletion() throws Throwable {
        FileAssetDeleteTask task = typedTask(205L, 305L, "memes/20261011/rebound.png", "PROCESSING", 0);
        task.setLeaseOwner("instance-a");
        FileAsset asset = asset(305L, task.getObjectKey());
        asset.setStatus("PENDING_DELETE");
        asset.setDeleteAfter(LocalDateTime.now().minusMinutes(1));
        FileAssetDeleteTaskMapper taskMapper = mock(FileAssetDeleteTaskMapper.class);
        when(taskMapper.selectById(205L)).thenReturn(task);
        when(taskMapper.selectByIdForUpdate(205L)).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(1);
        FileAssetMapper fileMapper = mock(FileAssetMapper.class);
        when(fileMapper.selectByIdForUpdate(305L)).thenReturn(asset);
        when(fileMapper.updateById(asset)).thenReturn(1);
        FileAssetReferenceMapper referenceMapper = mock(FileAssetReferenceMapper.class);
        when(referenceMapper.countByAssetId(305L)).thenReturn(1L);
        Object coordinator = createCoordinator(fileMapper, referenceMapper, taskMapper, new OssCleanupProperties());

        FileAsset eligible = (FileAsset) invoke(coordinator, "prepareForDelete",
                new Class<?>[]{Long.class, String.class}, 205L, "instance-a");

        assertNull(eligible);
        assertEquals("ACTIVE", asset.getStatus());
        assertEquals("CANCELED", task.getStatus());
    }

    @Test
    void schedulesTheFifthBackoffThenStopsRetrying() throws Throwable {
        FileAssetDeleteTask task = typedTask(206L, 306L, "home/20261011/banner.png", "PROCESSING", 4);
        task.setLeaseOwner("instance-a");
        FileAsset asset = asset(306L, task.getObjectKey());
        asset.setStatus("DELETING");
        FileAssetDeleteTaskMapper taskMapper = mock(FileAssetDeleteTaskMapper.class);
        when(taskMapper.selectById(206L)).thenReturn(task);
        when(taskMapper.selectByIdForUpdate(206L)).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(1);
        FileAssetMapper fileMapper = mock(FileAssetMapper.class);
        when(fileMapper.selectByIdForUpdate(306L)).thenReturn(asset);
        when(fileMapper.updateById(asset)).thenReturn(1);
        Object coordinator = createCoordinator(fileMapper, mock(FileAssetReferenceMapper.class),
                taskMapper, new OssCleanupProperties());

        invoke(coordinator, "recordFailure", new Class<?>[]{Long.class, String.class, String.class},
                206L, "instance-a", "OSS unavailable");

        assertEquals(5, task.getRetryCount());
        assertEquals("WAITING", task.getStatus());
        assertEquals("PENDING_DELETE", asset.getStatus());
        assertTrue(task.getNextAttemptAt().isAfter(LocalDateTime.now().plusHours(5)));

        task.setStatus("PROCESSING");
        task.setLeaseOwner("instance-a");
        asset.setStatus("DELETING");
        invoke(coordinator, "recordFailure", new Class<?>[]{Long.class, String.class, String.class},
                206L, "instance-a", "OSS still unavailable");

        assertEquals(5, task.getRetryCount(), "达到五次重试后不再增加重试次数");
        assertEquals("FAILED", task.getStatus());
    }

    private Object createWorker(Object coordinator, FileAssetBackfillStateMapper backfillMapper, OSS oss)
            throws Exception {
        Object worker = Class.forName(WORKER_CLASS).getDeclaredConstructor().newInstance();
        setFieldIfPresent(worker, "taskCoordinator", coordinator);
        setFieldIfPresent(worker, "fileAssetBackfillStateMapper", backfillMapper);
        setFieldIfPresent(worker, "ossClient", oss);
        setFieldIfPresent(worker, "bucketName", "test-bucket");
        setFieldIfPresent(worker, "cleanupProperties", new OssCleanupProperties());
        setFieldIfPresent(worker, "workerId", "instance-a");
        return worker;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object coordinator(List<Object> tasks, FileAsset asset, AtomicInteger doneCalls,
                               AtomicInteger permanentFailureCalls, AtomicInteger claimCalls,
                               AtomicInteger... retryCalls) throws Exception {
        Class<?> coordinatorType = Class.forName(COORDINATOR_CLASS);
        return mock((Class) coordinatorType, invocation -> switch (invocation.getMethod().getName()) {
            case "claimDueTasks" -> {
                claimCalls.incrementAndGet();
                yield tasks;
            }
            case "prepareForDelete" -> asset;
            case "markDone" -> {
                doneCalls.incrementAndGet();
                yield null;
            }
            case "markPermanentlyFailed" -> {
                permanentFailureCalls.incrementAndGet();
                yield null;
            }
            case "recordFailure" -> {
                if (retryCalls.length > 0) {
                    retryCalls[0].incrementAndGet();
                }
                yield null;
            }
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private FileAssetBackfillStateMapper completedBackfillMapper() {
        return mock(FileAssetBackfillStateMapper.class, invocation -> {
            if (!invocation.getMethod().getName().equals("selectCount")) {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            return invocation.getArgument(0) == null ? 1L : 0L;
        });
    }

    private Object task(Long id, String key, String leaseOwner) throws Exception {
        Object task = Class.forName(TASK_CLASS).getDeclaredConstructor().newInstance();
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "objectKey", key);
        ReflectionTestUtils.setField(task, "leaseOwner", leaseOwner);
        return task;
    }

    private FileAssetDeleteTask typedTask(Long id, Long assetId, String key, String status, int retryCount) {
        FileAssetDeleteTask task = new FileAssetDeleteTask();
        task.setId(id);
        task.setAssetId(assetId);
        task.setObjectKey(key);
        task.setReason("TEST");
        task.setStatus(status);
        task.setRetryCount(retryCount);
        task.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        return task;
    }

    private Object createCoordinator(FileAssetMapper fileMapper, FileAssetReferenceMapper referenceMapper,
                                     FileAssetDeleteTaskMapper taskMapper, OssCleanupProperties properties)
            throws Exception {
        Object coordinator = Class.forName(COORDINATOR_CLASS).getDeclaredConstructor().newInstance();
        setFieldIfPresent(coordinator, "fileAssetMapper", fileMapper);
        setFieldIfPresent(coordinator, "fileAssetReferenceMapper", referenceMapper);
        setFieldIfPresent(coordinator, "fileAssetDeleteTaskMapper", taskMapper);
        setFieldIfPresent(coordinator, "cleanupProperties", properties);
        return coordinator;
    }

    private FileAsset asset(Long id, String key) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setObjectKey(key);
        asset.setStatus("DELETING");
        return asset;
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
