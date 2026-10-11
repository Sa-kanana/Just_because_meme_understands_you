package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetBackfillStateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/** 中文注释：在历史引用回填完成后，安全、幂等地执行 OSS 延迟删除任务。 */
@Slf4j
@Service
public class FileAssetDeleteWorker {

    /** 中文注释：集中读取 OSS 清理批次、开关和对象前缀白名单的配置。 */
    @Resource
    private OssCleanupProperties cleanupProperties;

    /** 中文注释：负责短事务任务领取、引用复查和状态推进的协调器。 */
    @Resource
    private FileAssetDeleteTaskCoordinator taskCoordinator;

    /** 中文注释：用于删除对象并读取其存在状态的 OSS 客户端。 */
    @Resource
    private OSS ossClient;

    /** 中文注释：读取历史文件引用回填门禁的持久化映射器。 */
    @Resource
    private FileAssetBackfillStateMapper fileAssetBackfillStateMapper;

    /** 中文注释：存储业务对象的 OSS Bucket 名称。 */
    @Value("${oss.bucketName:}")
    private String bucketName;

    /** 中文注释：按配置间隔轮询有界 OSS 删除任务。 */
    @Scheduled(fixedDelayString = "${oss.cleanup.poll-interval-ms:60000}")
    public void runScheduledCleanup() {
        if (cleanupProperties != null && cleanupProperties.isEnabled()) {
            processDueTasks(cleanupProperties.getBatchSize());
        }
    }

    /**
     * 中文注释：历史回填完成后领取有界任务，确认资产无引用并在事务外调用 OSS。
     *
     * @param limit 本次希望处理的最大任务数，实际值还会受配置批次上限约束
     * @return 成功执行 OSS 删除并完成状态提交的任务数
     */
    public int processDueTasks(int limit) {
        if (cleanupProperties == null || !cleanupProperties.isEnabled() || !isLegacyBackfillComplete()) {
            return 0;
        }
        List<FileAssetDeleteTask> tasks = taskCoordinator.claimDueTasks(limit);
        int processed = 0;
        for (FileAssetDeleteTask task : tasks) {
            String leaseOwner = task.getLeaseOwner();
            try {
                FileAsset asset = taskCoordinator.prepareForDelete(task.getId(), leaseOwner);
                if (asset == null) {
                    continue;
                }
                String objectKey = asset.getObjectKey();
                if (!isSafeDeleteKey(objectKey) || !objectKey.equals(task.getObjectKey())) {
                    taskCoordinator.markPermanentlyFailed(task.getId(), leaseOwner,
                            "对象键不在删除白名单内、包含路径穿越或与任务快照不一致");
                    log.error("拒绝不安全的 OSS 删除任务 assetId={}, objectKey={}, taskId={}, retryCount={}",
                            asset.getId(), objectKey, task.getId(), task.getRetryCount());
                    continue;
                }
                if (!StringUtils.hasText(bucketName)) {
                    throw new IllegalStateException("未配置 OSS bucketName");
                }

                // 中文注释：OSS 删除是幂等操作，且在数据库领取/状态事务提交后执行。
                ossClient.deleteObject(bucketName, objectKey);
                taskCoordinator.markDone(task.getId(), leaseOwner);
                processed++;
            } catch (Exception exception) {
                String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                log.warn("OSS 资产删除请求失败 assetId={}, objectKey={}, taskId={}, retryCount={}",
                        task.getAssetId(), task.getObjectKey(), task.getId(), task.getRetryCount(), exception);
                taskCoordinator.recordFailure(task.getId(), leaseOwner, reason);
            }
        }
        return processed;
    }

    /** 中文注释：没有回填状态行或仍有未完成数据源时，禁止任何物理删除。 */
    private boolean isLegacyBackfillComplete() {
        long stateCount = fileAssetBackfillStateMapper.countAllStates(FileAssetLegacyBackfillRunner.BACKFILL_KEY);
        if (stateCount == 0) {
            return false;
        }
        return fileAssetBackfillStateMapper.countIncompleteStates(FileAssetLegacyBackfillRunner.BACKFILL_KEY) == 0;
    }

    /** 中文注释：只允许白名单业务前缀，并拦截外链、反斜杠、控制字符和路径穿越。 */
    private boolean isSafeDeleteKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.length() > 512
                || objectKey.startsWith("/") || objectKey.indexOf((char) 92) >= 0
                || objectKey.contains("://")
                || objectKey.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        String normalized = objectKey.toLowerCase(Locale.ROOT);
        List<String> protectedPrefixes = cleanupProperties.getProtectedPrefixes();
        if (protectedPrefixes != null && protectedPrefixes.stream()
                .filter(StringUtils::hasText)
                .map(prefix -> prefix.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::startsWith)) {
            return false;
        }
        List<String> allowedPrefixes = cleanupProperties.getAllowedPrefixes();
        if (allowedPrefixes == null || allowedPrefixes.stream()
                .filter(prefix -> StringUtils.hasText(prefix) && prefix.endsWith("/"))
                .noneMatch(objectKey::startsWith)) {
            return false;
        }
        for (String segment : objectKey.split("/")) {
            if (".".equals(segment) || "..".equals(segment) || !StringUtils.hasText(segment)) {
                return false;
            }
        }
        return true;
    }
}
