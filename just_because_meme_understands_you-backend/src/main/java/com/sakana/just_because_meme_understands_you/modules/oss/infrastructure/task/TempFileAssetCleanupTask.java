package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.task;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetDeleteTaskCoordinator;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssCleanupProperties;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 中文注释：每日扫描过期TEMP资产，仅将仍存在的OSS对象加入可靠删除队列。 */
@Slf4j
@Component
public class TempFileAssetCleanupTask {

    /** 中文注释：集中读取TEMP保留期限、清理开关和单次批次大小。 */
    @Resource
    private OssCleanupProperties cleanupProperties;

    /** 中文注释：按主键游标分页读取待检查的临时资产。 */
    @Resource
    private FileAssetMapper fileAssetMapper;

    /** 中文注释：在短事务中复查引用并创建延迟删除任务或审计状态。 */
    @Resource
    private FileAssetDeleteTaskCoordinator taskCoordinator;

    /** 中文注释：检查OSS对象是否仍然存在，不在数据库事务中调用网络服务。 */
    @Resource
    private OSS ossClient;

    /** 中文注释：存储业务对象的OSS Bucket名称。 */
    @Value("${oss.bucketName:}")
    private String bucketName;

    /** 中文注释：每天按配置时间检查过期TEMP资产。 */
    @Scheduled(cron = "${oss.cleanup.temp-cleanup-cron:0 0 3 * * *}")
    public void runScheduledCleanup() {
        if (cleanupProperties == null || !cleanupProperties.isEnabled()) {
            return;
        }
        long ttlDays = Math.max(1L, cleanupProperties.getTempAssetTtlDays());
        expireUnconfirmedAssets(LocalDateTime.now().minusDays(ttlDays));
    }

    /**
     * 中文注释：将截止时间之前的临时对象分批检查；对象不存在时也将审计记录标为DELETED。
     *
     * @param cutoff 临时资产创建时间截止点
     * @return 本次被标记为DELETED或加入可靠删除队列的资产数
     */
    public int expireUnconfirmedAssets(LocalDateTime cutoff) {
        if (cutoff == null || cleanupProperties == null || !cleanupProperties.isEnabled()
                || !StringUtils.hasText(bucketName)) {
            return 0;
        }
        int batchSize = Math.min(1000, Math.max(1, cleanupProperties.getBatchSize()));
        long ttlDays = Math.max(1L, cleanupProperties.getTempAssetTtlDays());
        if (cutoff.isAfter(LocalDateTime.now().minusDays(ttlDays))) {
            log.warn("拒绝使用尚未超过TEMP保留期限的清理截止时间 cutoff={}, ttlDays={}", cutoff, ttlDays);
            return 0;
        }

        int processed = 0;
        Long afterId = null;
        while (true) {
            List<FileAsset> assets = fileAssetMapper.selectExpiredTemporaryAssets(cutoff, afterId, batchSize);
            if (assets == null || assets.isEmpty()) {
                break;
            }
            for (FileAsset asset : assets) {
                if (asset.getId() == null) {
                    continue;
                }
                afterId = afterId == null ? asset.getId() : Math.max(afterId, asset.getId());
                try {
                    boolean exists = StringUtils.hasText(asset.getObjectKey())
                            && ossClient.doesObjectExist(bucketName, asset.getObjectKey());
                    if (taskCoordinator.expireTemporaryAsset(asset.getId(), cutoff, exists)) {
                        processed++;
                    }
                } catch (Exception exception) {
                    log.warn("检查过期TEMP资产失败，保留记录并等待下次扫描 assetId={}, objectKey={}",
                            asset.getId(), asset.getObjectKey(), exception);
                }
            }
            if (assets.size() < batchSize) {
                break;
            }
        }
        return processed;
    }
}
