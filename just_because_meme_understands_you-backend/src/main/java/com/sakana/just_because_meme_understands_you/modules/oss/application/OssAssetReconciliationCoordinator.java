package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileOrphanCandidate;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetBackfillStateMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileOrphanCandidateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 中文注释：逐个对象以短事务登记孤儿候选，满足全部安全条件后才创建可靠删除任务。 */
@Slf4j
@Service
public class OssAssetReconciliationCoordinator {

    /** 中文注释：对象观察后的状态，用于每日对账统计。 */
    public enum Observation { DISCOVERED, CONFIRMED_DRY_RUN, QUEUED, REFERENCED, SKIPPED }

    /** 中文注释：对账开关、观察期、延迟删除期和对象键白名单。 */
    @Resource private OssCleanupProperties cleanupProperties;
    /** 中文注释：按OSS对象键读取资产并防止对已登记资产创建孤儿任务。 */
    @Resource private FileAssetMapper fileAssetMapper;
    /** 中文注释：锁定候选记录，保证多实例对账幂等。 */
    @Resource private FileOrphanCandidateMapper candidateMapper;
    /** 中文注释：记录经复核后的孤儿对象删除任务。 */
    @Resource private FileAssetDeleteTaskMapper deleteTaskMapper;
    /** 中文注释：历史业务引用回填门禁，未完成时禁止创建孤儿删除任务。 */
    @Resource private FileAssetBackfillStateMapper backfillStateMapper;

    /** 中文注释：首次发现只登记；至少七天后复核，默认dry-run不创建删除任务。 */
    @Transactional
    public Observation observe(String objectKey, LocalDateTime objectModifiedAt) {
        if (cleanupProperties == null || !cleanupProperties.isReconcileEnabled()
                || !cleanupProperties.isSafeOrphanDeleteKey(objectKey)) {
            return Observation.SKIPPED;
        }
        LocalDateTime now = LocalDateTime.now();
        FileOrphanCandidate candidate = candidateMapper.selectByObjectKeyForUpdate(objectKey);
        FileAsset registered = fileAssetMapper.selectByObjectKeyForUpdate(objectKey);
        if (registered != null) {
            if (candidate != null && !"REFERENCED".equals(candidate.getStatus())
                    && !"QUEUED".equals(candidate.getStatus())) {
                candidate.setStatus("REFERENCED");
                candidate.setLastCheckResult("对象已登记为文件资产；禁止孤儿删除");
                candidate.setUpdatedAt(now);
                candidateMapper.updateById(candidate);
            }
            return Observation.REFERENCED;
        }
        if (candidate == null) {
            FileOrphanCandidate discovered = new FileOrphanCandidate();
            discovered.setObjectKey(objectKey);
            discovered.setStatus("DISCOVERED");
            discovered.setFirstSeenAt(now);
            discovered.setLastSeenAt(now);
            discovered.setLastCheckResult("首次发现；等待观察期后再次复核");
            discovered.setCreatedAt(now);
            discovered.setUpdatedAt(now);
            candidateMapper.insert(discovered);
            log.info("OSS孤儿候选首次发现 assetId={}, objectKey={}, bizType={}, bizId={}, reason={}, taskId={}, retryCount={}",
                    null, objectKey, null, null, "ORPHAN_DISCOVERED", null, 0);
            return Observation.DISCOVERED;
        }
        if ("QUEUED".equals(candidate.getStatus())) {
            return Observation.SKIPPED;
        }
        if ("REFERENCED".equals(candidate.getStatus())) {
            // 中文注释：对象曾受业务保护，若登记消失则重新开始完整观察期。
            candidate.setStatus("DISCOVERED");
            candidate.setFirstSeenAt(now);
            candidate.setConfirmedAt(null);
        }
        candidate.setLastSeenAt(now);
        candidate.setUpdatedAt(now);
        long reviewDays = Math.max(7L, cleanupProperties.getOrphanReviewDays());
        LocalDateTime cutoff = now.minusDays(reviewDays);
        if (candidate.getFirstSeenAt() == null || candidate.getFirstSeenAt().isAfter(cutoff)
                || objectModifiedAt == null || objectModifiedAt.isAfter(cutoff)) {
            candidate.setLastCheckResult("观察期不足或OSS对象修改时间不可确认；继续观察");
            candidateMapper.updateById(candidate);
            return Observation.DISCOVERED;
        }
        candidate.setConfirmedAt(now);
        if (cleanupProperties.isReconcileDryRun()) {
            candidate.setLastCheckResult("观察期已满；dry-run仅记录，不创建删除任务");
            candidateMapper.updateById(candidate);
            return Observation.CONFIRMED_DRY_RUN;
        }
        if (backfillStateMapper.countAllStates(FileAssetLegacyBackfillRunner.BACKFILL_KEY) == 0
                || backfillStateMapper.countIncompleteStates(FileAssetLegacyBackfillRunner.BACKFILL_KEY) != 0) {
            candidate.setLastCheckResult("历史引用回填未完成；禁止创建删除任务");
            candidateMapper.updateById(candidate);
            return Observation.SKIPPED;
        }
        LocalDateTime deleteAfter = now.plusDays(Math.max(1L, cleanupProperties.getDeletionDelayDays()));
        FileAsset asset = new FileAsset();
        asset.setObjectKey(objectKey);
        asset.setScene(sceneFor(objectKey));
        asset.setStatus("PENDING_DELETE");
        asset.setDeleteAfter(deleteAfter);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        if (fileAssetMapper.insert(asset) != 1 || asset.getId() == null) {
            throw new IllegalStateException("孤儿候选创建资产记录失败");
        }
        FileAssetDeleteTask task = new FileAssetDeleteTask();
        task.setAssetId(asset.getId());
        task.setObjectKey(objectKey);
        task.setReason("ORPHAN_RECONCILIATION");
        task.setStatus("WAITING");
        task.setRetryCount(0);
        task.setNextAttemptAt(deleteAfter);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (deleteTaskMapper.insert(task) != 1) {
            throw new IllegalStateException("孤儿候选创建删除任务失败");
        }
        candidate.setStatus("QUEUED");
        candidate.setLastCheckResult("复核通过；已创建延迟删除任务，Worker删除前仍会检查引用");
        candidateMapper.updateById(candidate);
        log.warn("OSS孤儿候选已入队 assetId={}, objectKey={}, bizType={}, bizId={}, reason={}, taskId={}, retryCount={}",
                asset.getId(), objectKey, null, null, task.getReason(), task.getId(), 0);
        return Observation.QUEUED;
    }

    /** 中文注释：由允许前缀推导审计场景，禁止将未受控路径登记为资产。 */
    private String sceneFor(String key) {
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : "unknown";
    }
}
