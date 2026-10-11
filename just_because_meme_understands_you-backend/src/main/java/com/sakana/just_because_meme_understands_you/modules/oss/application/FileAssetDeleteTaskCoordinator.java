package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.modules.oss.domain.FileAssetStatus;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 中文注释：在短数据库事务中领取、校验并推进 OSS 删除任务状态。 */
@Slf4j
@Service
public class FileAssetDeleteTaskCoordinator {

    /** 中文注释：不同重试次数对应的固定退避间隔。 */
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6));

    /** 中文注释：包含批次、租约和删除等待参数的 OSS 清理配置。 */
    @Resource
    private OssCleanupProperties cleanupProperties;

    /** 中文注释：锁定并更新文件资产状态的持久化映射器。 */
    @Resource
    private FileAssetMapper fileAssetMapper;

    /** 中文注释：复查资产是否仍被业务字段引用的持久化映射器。 */
    @Resource
    private FileAssetReferenceMapper fileAssetReferenceMapper;

    /** 中文注释：领取和更新 OSS 删除任务的持久化映射器。 */
    @Resource
    private FileAssetDeleteTaskMapper fileAssetDeleteTaskMapper;

    /** 中文注释：补齐空的实例配置，确保多实例租约可以区分 Worker。 */
    @PostConstruct
    public void ensureWorkerId() {
        if (cleanupProperties == null || StringUtils.hasText(cleanupProperties.getWorkerId())) {
            return;
        }
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            hostName = "unknown-host";
        }
        cleanupProperties.setWorkerId(hostName + "-" + java.util.UUID.randomUUID());
    }

    /** 中文注释：用行锁跳过其他实例正在处理的任务，并给有界批次设置租约。 */
    @Transactional
    public List<FileAssetDeleteTask> claimDueTasks(int requestedLimit) {
        if (cleanupProperties == null || !cleanupProperties.isEnabled()) {
            return List.of();
        }
        if (requestedLimit <= 0) {
            return List.of();
        }
        int batchSize = Math.max(1, cleanupProperties.getBatchSize());
        int limit = Math.min(requestedLimit, batchSize);
        LocalDateTime now = LocalDateTime.now();
        List<FileAssetDeleteTask> tasks = fileAssetDeleteTaskMapper.selectDueForUpdate(now, limit);
        for (FileAssetDeleteTask task : tasks) {
            task.setStatus("PROCESSING");
            task.setLeaseOwner(cleanupProperties.getWorkerId());
            task.setLeaseUntil(now.plusSeconds(Math.max(30L, cleanupProperties.getLeaseSeconds())));
            task.setStartedAt(now);
            task.setUpdatedAt(now);
            fileAssetDeleteTaskMapper.updateById(task);
        }
        return tasks;
    }

    /**
     * 中文注释：删除 OSS 对象前锁定资产并二次检查任务租约、状态、延迟时间和引用数。
     *
     * @param taskId 删除任务主键
     * @param leaseOwner 当前领取该任务的 Worker 标识
     * @return 已转换为 DELETING 的资产；任务失效或仍被引用时返回 null
     */
    @Transactional
    public FileAsset prepareForDelete(Long taskId, String leaseOwner) {
        FileAssetDeleteTask snapshot = fileAssetDeleteTaskMapper.selectById(taskId);
        if (snapshot == null || snapshot.getAssetId() == null) {
            return null;
        }
        FileAsset asset = fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
        FileAssetDeleteTask task = fileAssetDeleteTaskMapper.selectByIdForUpdate(taskId);
        if (task == null || asset == null || !"PROCESSING".equals(task.getStatus())
                || !StringUtils.hasText(leaseOwner) || !leaseOwner.equals(task.getLeaseOwner())) {
            return null;
        }
        if (FileAssetStatus.DELETED.name().equals(asset.getStatus())) {
            finishTask(task, "DONE", null);
            return null;
        }
        if (!Objects.equals(task.getObjectKey(), asset.getObjectKey())) {
            finishTask(task, "FAILED", "任务对象键与资产当前对象键不一致");
            return null;
        }
        long referenceCount = fileAssetReferenceMapper.countByAssetId(asset.getId());
        if (referenceCount > 0) {
            asset.setStatus(FileAssetStatus.ACTIVE.name());
            asset.setDeleteAfter(null);
            asset.setUpdatedAt(LocalDateTime.now());
            fileAssetMapper.updateById(asset);
            finishTask(task, "CANCELED", null);
            return null;
        }
        if (FileAssetStatus.PENDING_DELETE.name().equals(asset.getStatus())
                && asset.getDeleteAfter() != null && asset.getDeleteAfter().isAfter(LocalDateTime.now())) {
            task.setStatus("WAITING");
            task.setNextAttemptAt(asset.getDeleteAfter());
            task.setLeaseOwner(null);
            task.setLeaseUntil(null);
            task.setUpdatedAt(LocalDateTime.now());
            fileAssetDeleteTaskMapper.updateById(task);
            return null;
        }
        if (!FileAssetStatus.PENDING_DELETE.name().equals(asset.getStatus())
                && !FileAssetStatus.DELETING.name().equals(asset.getStatus())) {
            finishTask(task, "CANCELED", null);
            return null;
        }

        asset.setStatus(FileAssetStatus.DELETING.name());
        asset.setUpdatedAt(LocalDateTime.now());
        if (fileAssetMapper.updateById(asset) != 1) {
            throw new IllegalStateException("锁定文件资产后更新 DELETING 状态失败");
        }
        return asset;
    }

    /** 中文注释：OSS 删除成功后将资产和任务一并标记为完成。 */
    @Transactional
    public void markDone(Long taskId, String leaseOwner) {
        FileAssetDeleteTask snapshot = fileAssetDeleteTaskMapper.selectById(taskId);
        if (snapshot == null || snapshot.getAssetId() == null) {
            return;
        }
        FileAsset asset = fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
        FileAssetDeleteTask task = fileAssetDeleteTaskMapper.selectByIdForUpdate(taskId);
        if (!isOwnedProcessingTask(task, leaseOwner) || asset == null
                || !FileAssetStatus.DELETING.name().equals(asset.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        asset.setStatus(FileAssetStatus.DELETED.name());
        asset.setDeletedAt(now);
        asset.setDeleteAfter(null);
        asset.setUpdatedAt(now);
        fileAssetMapper.updateById(asset);
        finishTask(task, "DONE", null);
        log.info("OSS 资产删除完成 assetId={}, objectKey={}, taskId={}, retryCount={}",
                asset.getId(), asset.getObjectKey(), task.getId(), task.getRetryCount());
    }

    /** 中文注释：OSS 请求失败时使用固定退避重新排队，超过上限后停止自动重试。 */
    @Transactional
    public void recordFailure(Long taskId, String leaseOwner, String errorMessage) {
        updateFailureState(taskId, leaseOwner, errorMessage, false);
    }

    /** 中文注释：对象键违反删除安全规则时终止任务并记录错误，等待人工检查。 */
    @Transactional
    public void markPermanentlyFailed(Long taskId, String leaseOwner, String errorMessage) {
        updateFailureState(taskId, leaseOwner, errorMessage, true);
    }

    /** 中文注释：返回该次失败后应安排的退避时长；返回 null 表示重试次数已耗尽。 */
    static Duration retryDelayForRetryCount(int alreadyScheduledRetries) {
        if (alreadyScheduledRetries < 0 || alreadyScheduledRetries >= RETRY_DELAYS.size()) {
            return null;
        }
        return RETRY_DELAYS.get(alreadyScheduledRetries);
    }

    private void updateFailureState(Long taskId, String leaseOwner, String errorMessage, boolean permanent) {
        FileAssetDeleteTask snapshot = fileAssetDeleteTaskMapper.selectById(taskId);
        if (snapshot == null || snapshot.getAssetId() == null) {
            return;
        }
        FileAsset asset = fileAssetMapper.selectByIdForUpdate(snapshot.getAssetId());
        FileAssetDeleteTask task = fileAssetDeleteTaskMapper.selectByIdForUpdate(taskId);
        if (!isOwnedProcessingTask(task, leaseOwner)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (asset != null && FileAssetStatus.DELETING.name().equals(asset.getStatus())) {
            asset.setStatus(FileAssetStatus.PENDING_DELETE.name());
            asset.setUpdatedAt(now);
            fileAssetMapper.updateById(asset);
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        Duration retryDelay = permanent ? null : retryDelayForRetryCount(retryCount);
        if (retryDelay == null || retryCount >= Math.max(0, cleanupProperties.getMaxRetries())) {
            finishTask(task, "FAILED", errorMessage);
            log.error("OSS 资产删除任务进入失败终态 assetId={}, objectKey={}, taskId={}, retryCount={}, reason={}",
                    task.getAssetId(), task.getObjectKey(), task.getId(), retryCount, task.getReason());
            return;
        }

        task.setRetryCount(retryCount + 1);
        task.setStatus("WAITING");
        task.setNextAttemptAt(now.plus(retryDelay));
        task.setLeaseOwner(null);
        task.setLeaseUntil(null);
        task.setLastError(truncate(errorMessage));
        task.setUpdatedAt(now);
        fileAssetDeleteTaskMapper.updateById(task);
        log.warn("OSS 资产删除失败并重新排队 assetId={}, objectKey={}, taskId={}, retryCount={}, nextAttemptAt={}",
                task.getAssetId(), task.getObjectKey(), task.getId(), task.getRetryCount(), task.getNextAttemptAt());
    }

    private boolean isOwnedProcessingTask(FileAssetDeleteTask task, String leaseOwner) {
        return task != null && "PROCESSING".equals(task.getStatus())
                && StringUtils.hasText(leaseOwner) && leaseOwner.equals(task.getLeaseOwner());
    }

    private void finishTask(FileAssetDeleteTask task, String status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(status);
        task.setFinishedAt(now);
        task.setLeaseOwner(null);
        task.setLeaseUntil(null);
        task.setLastError(truncate(errorMessage));
        task.setUpdatedAt(now);
        fileAssetDeleteTaskMapper.updateById(task);
    }

    private String truncate(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        String normalized = errorMessage.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}
