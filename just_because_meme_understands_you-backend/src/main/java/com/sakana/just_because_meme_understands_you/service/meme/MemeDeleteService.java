package com.sakana.just_because_meme_understands_you.service.meme;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.service.ai.IAiIngestService;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.vo.MemeDeleteResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemePurgeResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemeRestoreResponseVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 梗下架 / 恢复 / 申诉 / 永久封禁。
 *
 * <pre>
 * 正常(1) ──主动下架──► 主动下架(3) ──随时手动──► 正常(1)
 * 正常(1)/审核中(2) ──风控下架──► 锁定(5) ──申诉──► 恢复审核中(6)
 *                                              ├─通过──► 正常(1)
 *                                              └─驳回──► 锁定(5)；多次驳回──► 永久封禁(4)
 * </pre>
 */
@Slf4j
@Service
public class MemeDeleteService {

    private static final int RESOURCE_STATUS_ACTIVE = 1;
    private static final int RESOURCE_STATUS_INACTIVE = 0;
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${meme.purge-cooldown-days:0}")
    private int purgeCooldownDays;

    @Value("${meme.appeal-reject-max:3}")
    private int appealRejectMax;

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private MemeResourceMapper memeResourceMapper;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Resource
    private MemePublishAsyncHandler memePublishAsyncHandler;

    @Resource
    private MemeOssCleanupHandler memeOssCleanupHandler;

    @Resource
    private IUserProfileService userProfileService;

    @Resource
    private IMemeService memeService;

    @Resource
    private IAiIngestService aiIngestService;

    /**
     * 用户主动下架：1/2 → 3。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemeDeleteResponseVO deleteOwnMeme(Long userId, Long memeId) {
        Meme meme = loadOwnedMeme(userId, memeId);
        assertNotPurged(meme);
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)) {
            return toDeleteResponse(memeId, meme.getOfflineAt() != null ? meme.getOfflineAt() : meme.getUpdateTime());
        }
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_LOCKED)
                || Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_RESTORE_REVIEWING)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "该梗已被风控锁定，无法主动下架");
        }
        if (!Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_NORMAL)
                && !Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_REVIEWING)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "当前状态不可下架");
        }
        LocalDateTime now = markStatusChange(
                meme,
                MemeVisibilitySupport.STATUS_OFFLINE,
                "用户主动下架",
                true
        );
        return toDeleteResponse(memeId, now);
    }

    /**
     * 管理端风控下架：1/2/3 → 5。
     */
    @Transactional(rollbackFor = Exception.class)
    public Meme markLockedByAdmin(Long memeId, String reason) {
        Meme meme = loadMeme(memeId);
        assertNotPurged(meme);
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_LOCKED)) {
            return meme;
        }
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_RESTORE_REVIEWING)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "恢复审核中的梗请使用驳回");
        }
        if (!Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_NORMAL)
                && !Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_REVIEWING)
                && !Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "当前状态不可风控下架");
        }
        String offlineReason = StringUtils.hasText(reason) ? reason.trim() : "违规/风控下架";
        markStatusChange(meme, MemeVisibilitySupport.STATUS_LOCKED, offlineReason, true);
        return meme;
    }

    /**
     * 首发审核拒绝：2 → 5。
     */
    @Transactional(rollbackFor = Exception.class)
    public Meme rejectFirstReviewByAdmin(Long memeId, String reason) {
        Meme meme = loadMeme(memeId);
        assertNotPurged(meme);
        if (!Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_REVIEWING)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅首次审核中的梗可拒绝");
        }
        String offlineReason = StringUtils.hasText(reason) ? reason.trim() : "审核未通过";
        markStatusChange(meme, MemeVisibilitySupport.STATUS_LOCKED, offlineReason, true);
        return meme;
    }

    /**
     * 用户恢复：
     * <ul>
     *   <li>主动下架(3) → 直接重新上架(1)</li>
     *   <li>风控锁定(5) → 提交整改申诉(6)</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public MemeRestoreResponseVO restoreOwnMeme(Long userId, Long memeId) {
        Meme meme = loadOwnedMeme(userId, memeId);
        assertNotPurged(meme);
        Integer status = meme.getStatus();
        if (Objects.equals(status, MemeVisibilitySupport.STATUS_OFFLINE)) {
            LocalDateTime now = relistToNormal(meme);
            return toRestoreResponse(memeId, MemeVisibilitySupport.STATUS_NORMAL, now);
        }
        if (Objects.equals(status, MemeVisibilitySupport.STATUS_LOCKED)) {
            LocalDateTime now = submitAppeal(meme);
            return toRestoreResponse(memeId, MemeVisibilitySupport.STATUS_RESTORE_REVIEWING, now);
        }
        if (Objects.equals(status, MemeVisibilitySupport.STATUS_RESTORE_REVIEWING)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "已在恢复审核中，请等待结果");
        }
        throw new BizException(Result.CODE_BAD_REQUEST, "当前状态不可恢复");
    }

    /**
     * 管理端代为上架主动下架的梗：3 → 1；或强制解封锁定：5 → 1。
     */
    @Transactional(rollbackFor = Exception.class)
    public Meme forceOnlineByAdmin(Long memeId) {
        Meme meme = loadMeme(memeId);
        assertNotPurged(meme);
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)
                || Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_LOCKED)) {
            relistToNormal(meme);
            return meme;
        }
        throw new BizException(Result.CODE_BAD_REQUEST, "仅主动下架或锁定中的梗可强制上架");
    }

    /**
     * 恢复审核驳回：6 → 5；达上限 → 4。
     */
    @Transactional(rollbackFor = Exception.class)
    public Meme rejectAppealByAdmin(Long memeId, String reason) {
        Meme meme = loadMeme(memeId);
        assertNotPurged(meme);
        if (!Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_RESTORE_REVIEWING)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅恢复审核中的梗可驳回");
        }
        int rejects = meme.getAppealRejectCount() == null ? 0 : meme.getAppealRejectCount();
        rejects += 1;
        LocalDateTime now = LocalDateTime.now();
        meme.setAppealRejectCount(rejects);
        meme.setUpdateTime(now);
        String offlineReason = StringUtils.hasText(reason) ? reason.trim() : "整改申诉未通过";
        meme.setOfflineReason(offlineReason);

        if (rejects >= Math.max(1, appealRejectMax)) {
            meme.setStatus(MemeVisibilitySupport.STATUS_PURGED);
            meme.setPurgedAt(now);
            memeMapper.updateById(meme);
            Long memeIdLong = meme.getId().longValue();
            deactivateResources(memeIdLong);
            Meme snapshot = new Meme().setId(meme.getId()).setImage(meme.getImage());
            Long ownerId = meme.getUserId();
            scheduleAfterCommit(() -> runAfterPurge(ownerId, memeIdLong, snapshot));
            return meme;
        }

        meme.setStatus(MemeVisibilitySupport.STATUS_LOCKED);
        meme.setOfflineAt(now);
        memeMapper.updateById(meme);
        Long memeIdLong = meme.getId().longValue();
        Long userId = meme.getUserId();
        scheduleAfterCommit(() -> {
            evictCaches(userId, memeIdLong);
            try {
                aiIngestService.removeMeme(memeIdLong);
            } catch (Exception e) {
                log.warn("申诉驳回后清理向量失败 memeId={}", memeIdLong, e);
            }
        });
        return meme;
    }

    /**
     * 彻底删除：仅主动下架(3) → 4。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemePurgeResponseVO purgeOwnMeme(Long userId, Long memeId) {
        Meme meme = loadOwnedMeme(userId, memeId);
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_PURGED)) {
            return toPurgeResponse(memeId, meme.getPurgedAt() != null ? meme.getPurgedAt() : meme.getUpdateTime());
        }
        if (!Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_OFFLINE)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅主动下架的梗可彻底删除；风控锁定请走申诉");
        }
        LocalDateTime offlineAt = meme.getOfflineAt() != null ? meme.getOfflineAt() : meme.getUpdateTime();
        assertPurgeCooldown(offlineAt);

        LocalDateTime now = LocalDateTime.now();
        meme.setStatus(MemeVisibilitySupport.STATUS_PURGED);
        meme.setPurgedAt(now);
        meme.setUpdateTime(now);
        memeMapper.updateById(meme);

        Long memeIdLong = memeId;
        deactivateResources(memeIdLong);
        Meme snapshot = new Meme().setId(meme.getId()).setImage(meme.getImage());
        Long ownerId = meme.getUserId();
        scheduleAfterCommit(() -> runAfterPurge(ownerId, memeIdLong, snapshot));
        return toPurgeResponse(memeId, now);
    }

    private LocalDateTime markStatusChange(Meme meme, int targetStatus, String reason, boolean recordOfflineMeta) {
        Long memeId = meme.getId().longValue();
        Integer previousStatus = meme.getStatus();
        LocalDateTime now = LocalDateTime.now();
        meme.setStatus(targetStatus);
        meme.setUpdateTime(now);
        if (recordOfflineMeta) {
            meme.setOfflineAt(now);
            meme.setOfflineFromStatus(previousStatus);
            meme.setOfflineReason(reason);
        }
        memeMapper.updateById(meme);

        deactivateResources(memeId);
        List<Integer> tagIds = loadTagIdsByMemeId(memeId);
        Long userId = meme.getUserId();
        boolean wasPublic = Objects.equals(previousStatus, MemeVisibilitySupport.STATUS_NORMAL);
        scheduleAfterCommit(() -> runAfterOffline(userId, memeId, tagIds, wasPublic));
        return now;
    }

    private LocalDateTime relistToNormal(Meme meme) {
        Long memeId = meme.getId().longValue();
        LocalDateTime now = LocalDateTime.now();
        meme.setStatus(MemeVisibilitySupport.STATUS_NORMAL);
        meme.setUpdateTime(now);
        meme.setOfflineReason(null);
        meme.setAppealRejectCount(0);
        memeMapper.updateById(meme);

        reactivateResources(memeId);
        List<Integer> tagIds = loadTagIdsByMemeId(memeId);
        Long userId = meme.getUserId();
        scheduleAfterCommit(() -> runAfterRelist(userId, memeId, tagIds));
        return now;
    }

    private LocalDateTime submitAppeal(Meme meme) {
        Long memeId = meme.getId().longValue();
        LocalDateTime now = LocalDateTime.now();
        meme.setStatus(MemeVisibilitySupport.STATUS_RESTORE_REVIEWING);
        meme.setUpdateTime(now);
        memeMapper.updateById(meme);

        reactivateResources(memeId);
        Long userId = meme.getUserId();
        scheduleAfterCommit(() -> {
            evictCaches(userId, memeId);
            try {
                aiIngestService.removeMeme(memeId);
            } catch (Exception e) {
                log.warn("提交申诉后清理向量失败 memeId={}", memeId, e);
            }
        });
        return now;
    }

    private Meme loadOwnedMeme(Long userId, Long memeId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        Meme meme = loadMeme(memeId);
        if (meme.getUserId() == null || !Objects.equals(meme.getUserId(), userId)) {
            throw new BizException(Result.CODE_FORBIDDEN, "无权操作该梗");
        }
        return meme;
    }

    private Meme loadMeme(Long memeId) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        return meme;
    }

    private void assertNotPurged(Meme meme) {
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_PURGED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或已永久封禁");
        }
    }

    private void assertPurgeCooldown(LocalDateTime offlineAt) {
        if (purgeCooldownDays <= 0 || offlineAt == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(offlineAt, LocalDateTime.now());
        if (days < purgeCooldownDays) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "下架后需满 " + purgeCooldownDays + " 天才可彻底删除");
        }
    }

    private void deactivateResources(Long memeId) {
        memeResourceMapper.update(null, new LambdaUpdateWrapper<MemeResource>()
                .eq(MemeResource::getMemeId, memeId)
                .eq(MemeResource::getStatus, RESOURCE_STATUS_ACTIVE)
                .set(MemeResource::getStatus, RESOURCE_STATUS_INACTIVE));
    }

    private void reactivateResources(Long memeId) {
        memeResourceMapper.update(null, new LambdaUpdateWrapper<MemeResource>()
                .eq(MemeResource::getMemeId, memeId)
                .eq(MemeResource::getStatus, RESOURCE_STATUS_INACTIVE)
                .set(MemeResource::getStatus, RESOURCE_STATUS_ACTIVE));
    }

    private List<Integer> loadTagIdsByMemeId(Long memeId) {
        List<MemeTagRelation> relations = memeTagRelationMapper.selectList(
                new LambdaQueryWrapper<MemeTagRelation>()
                        .eq(MemeTagRelation::getMemeId, memeId.intValue())
        );
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<Integer> tagIds = new ArrayList<>(relations.size());
        for (MemeTagRelation relation : relations) {
            if (relation != null && relation.getMemeTagId() != null && relation.getMemeTagId() > 0) {
                tagIds.add(relation.getMemeTagId());
            }
        }
        return tagIds;
    }

    private void scheduleAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void runAfterOffline(Long userId, Long memeId, List<Integer> tagIds, boolean wasPublic) {
        evictCaches(userId, memeId);
        if (wasPublic) {
            try {
                memePublishAsyncHandler.decrementTagRelatedQuantity(tagIds);
            } catch (Exception e) {
                log.warn("标签计数回滚失败 memeId={}", memeId, e);
            }
        }
        try {
            aiIngestService.removeMeme(memeId);
        } catch (Exception e) {
            log.warn("AI 向量删除失败 memeId={}", memeId, e);
        }
    }

    private void runAfterRelist(Long userId, Long memeId, List<Integer> tagIds) {
        evictCaches(userId, memeId);
        try {
            memePublishAsyncHandler.incrementTagRelatedQuantity(tagIds);
        } catch (Exception e) {
            log.warn("重新上架标签计数失败 memeId={}", memeId, e);
        }
        try {
            aiIngestService.syncMeme(memeId);
        } catch (Exception e) {
            log.warn("重新上架灌库失败 memeId={}", memeId, e);
        }
    }

    private void runAfterPurge(Long userId, Long memeId, Meme snapshot) {
        evictCaches(userId, memeId);
        try {
            memeOssCleanupHandler.cleanupMemeAssets(snapshot);
        } catch (Exception e) {
            log.warn("OSS 清理失败 memeId={}", memeId, e);
        }
        try {
            aiIngestService.removeMeme(memeId);
        } catch (Exception e) {
            log.warn("AI 向量删除失败(purge) memeId={}", memeId, e);
        }
    }

    private void evictCaches(Long userId, Long memeId) {
        try {
            memeService.evictMemeDetailCache(memeId);
        } catch (Exception e) {
            log.warn("清理梗详情缓存失败 memeId={}", memeId, e);
        }
        if (userId != null) {
            try {
                userProfileService.evictUserMemesCache(userId);
            } catch (Exception e) {
                log.warn("清理用户发布列表缓存失败 userId={}", userId, e);
            }
        }
    }

    private MemeDeleteResponseVO toDeleteResponse(Long memeId, LocalDateTime deletedAt) {
        MemeDeleteResponseVO vo = new MemeDeleteResponseVO();
        vo.setMemeId(memeId);
        vo.setStatus(MemeVisibilitySupport.STATUS_OFFLINE);
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(MemeVisibilitySupport.STATUS_OFFLINE));
        vo.setDeletedAt(formatTime(deletedAt));
        return vo;
    }

    private MemeRestoreResponseVO toRestoreResponse(Long memeId, int status, LocalDateTime restoredAt) {
        MemeRestoreResponseVO vo = new MemeRestoreResponseVO();
        vo.setMemeId(memeId);
        vo.setStatus(status);
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(status));
        vo.setRestoredAt(formatTime(restoredAt));
        return vo;
    }

    private MemePurgeResponseVO toPurgeResponse(Long memeId, LocalDateTime purgedAt) {
        MemePurgeResponseVO vo = new MemePurgeResponseVO();
        vo.setMemeId(memeId);
        vo.setStatus(MemeVisibilitySupport.STATUS_PURGED);
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(MemeVisibilitySupport.STATUS_PURGED));
        vo.setPurgedAt(formatTime(purgedAt));
        return vo;
    }

    private String formatTime(LocalDateTime time) {
        LocalDateTime value = time != null ? time : LocalDateTime.now();
        return value.format(DATETIME_FORMAT);
    }
}
