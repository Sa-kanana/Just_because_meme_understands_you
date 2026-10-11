package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.task;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.OSSObjectSummary;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssAssetReconciliationCoordinator;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssCleanupProperties;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileOrphanCandidateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 中文注释：按允许的业务前缀分页对账OSS对象；默认关闭扫描且dry-run不安排删除。 */
@Slf4j
@Component
public class OssAssetReconciliationTask {

    /** 中文注释：本次扫描总数、首次发现数、仅观察数、入队数和跳过数。 */
    public record ReconciliationSummary(long scanned, long discovered, long dryRunConfirmed,
                                        long queued, long skipped) { }

    /** 中文注释：读取OSS列举分页。 */
    @Resource private OSS ossClient;
    /** 中文注释：对每个对象执行独立数据库事务。 */
    @Resource private OssAssetReconciliationCoordinator coordinator;
    /** 中文注释：读取对账开关、扫描前缀、页大小和安全白名单。 */
    @Resource private OssCleanupProperties cleanupProperties;
    /** 中文注释：读取资产各生命周期状态的每日统计。 */
    @Resource private FileAssetMapper fileAssetMapper;
    /** 中文注释：读取孤儿候选各状态的每日统计。 */
    @Resource private FileOrphanCandidateMapper candidateMapper;
    /** 中文注释：存储业务文件的OSS Bucket名称。 */
    @Value("${oss.bucketName:}") private String bucketName;

    /** 中文注释：按配置Cron每日运行一次；默认关闭，需明确配置启用。 */
    @Scheduled(cron = "${oss.cleanup.reconcile-cron:0 30 3 * * *}")
    public void runScheduledReconciliation() {
        reconcile();
    }

    /** 中文注释：仅列举配置前缀并逐页推进，单个候选失败不影响同页其他对象。 */
    public ReconciliationSummary reconcile() {
        if (cleanupProperties == null || !cleanupProperties.isReconcileEnabled()
                || !StringUtils.hasText(bucketName)) {
            return new ReconciliationSummary(0, 0, 0, 0, 0);
        }
        long scanned = 0, discovered = 0, confirmed = 0, queued = 0, skipped = 0;
        List<String> prefixes = cleanupProperties.getReconcilePrefixes();
        if (prefixes == null) {
            return new ReconciliationSummary(0, 0, 0, 0, 0);
        }
        int pageSize = Math.min(1000, Math.max(1, cleanupProperties.getReconcilePageSize()));
        for (String prefix : prefixes) {
            if (!StringUtils.hasText(prefix) || !prefix.endsWith("/")
                    || !cleanupProperties.isSafeDeleteKey(prefix + "probe")) {
                log.warn("跳过不安全的OSS对账前缀 prefix={}", prefix);
                continue;
            }
            String marker = null;
            do {
                ListObjectsRequest request = new ListObjectsRequest(bucketName, prefix, marker, null, pageSize);
                ObjectListing page = ossClient.listObjects(request);
                for (OSSObjectSummary object : page.getObjectSummaries()) {
                    scanned++;
                    String key = object.getKey();
                    if (!key.startsWith(prefix) || !cleanupProperties.isSafeDeleteKey(key)) {
                        skipped++;
                        continue;
                    }
                    try {
                        LocalDateTime modifiedAt = object.getLastModified() == null ? null
                                : LocalDateTime.ofInstant(object.getLastModified().toInstant(), ZoneId.systemDefault());
                        switch (coordinator.observe(key, modifiedAt)) {
                            case DISCOVERED -> discovered++;
                            case CONFIRMED_DRY_RUN -> confirmed++;
                            case QUEUED -> queued++;
                            default -> skipped++;
                        }
                    } catch (Exception exception) {
                        skipped++;
                        log.error("OSS孤儿对象对账失败 objectKey={}，下次扫描重试", key, exception);
                    }
                }
                if (!page.isTruncated()) {
                    break;
                }
                String nextMarker = page.getNextMarker();
                if (!StringUtils.hasText(nextMarker) || nextMarker.equals(marker)) {
                    throw new IllegalStateException("OSS对象分页标记没有推进，停止对账以避免死循环");
                }
                marker = nextMarker;
            } while (true);
        }
        ReconciliationSummary summary = new ReconciliationSummary(scanned, discovered, confirmed, queued, skipped);
        log.info("OSS对账完成 dryRun={}, scanned={}, discovered={}, confirmed={}, queued={}, skipped={}, "
                        + "assetsTemp={}, assetsActive={}, assetsPending={}, candidatesDiscovered={}, candidatesQueued={}",
                cleanupProperties.isReconcileDryRun(), scanned, discovered, confirmed, queued, skipped,
                fileAssetMapper.countByStatus("TEMP"), fileAssetMapper.countByStatus("ACTIVE"),
                fileAssetMapper.countByStatus("PENDING_DELETE"), candidateMapper.countByStatus("DISCOVERED"),
                candidateMapper.countByStatus("QUEUED"));
        return summary;
    }
}
