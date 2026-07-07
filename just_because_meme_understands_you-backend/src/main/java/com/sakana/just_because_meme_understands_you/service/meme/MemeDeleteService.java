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



import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.time.temporal.ChronoUnit;

import java.util.ArrayList;

import java.util.List;

import java.util.Objects;



/**

 * 用户发布梗的生命周期：下架、恢复、彻底删除。

 */

@Slf4j

@Service

public class MemeDeleteService {



    private static final int STATUS_REVIEWING = 2;

    private static final int STATUS_OFFLINE = 3;

    private static final int STATUS_PURGED = 4;

    private static final int RESOURCE_STATUS_ACTIVE = 1;

    private static final int RESOURCE_STATUS_INACTIVE = 0;

    private static final String STATUS_DESC_DELETED = "已下架";

    private static final String STATUS_DESC_REVIEWING = "审核中";

    private static final String STATUS_DESC_PURGED = "已彻底删除";

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");



    @Value("${meme.restore-days:30}")

    private int restoreDays;



    @Value("${meme.purge-cooldown-days:0}")

    private int purgeCooldownDays;



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



    /**

     * 删除当前用户发布的梗（软下架 status=3）。

     */

    @Transactional(rollbackFor = Exception.class)

    public MemeDeleteResponseVO deleteOwnMeme(Long userId, Long memeId) {

        Meme meme = loadOwnedMeme(userId, memeId);

        if (Objects.equals(meme.getStatus(), STATUS_PURGED)) {

            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");

        }



        LocalDateTime deletedAt = meme.getUpdateTime() != null ? meme.getUpdateTime() : LocalDateTime.now();

        if (Objects.equals(meme.getStatus(), STATUS_OFFLINE)) {

            return toDeleteResponse(memeId, deletedAt);

        }



        LocalDateTime now = LocalDateTime.now();

        meme.setStatus(STATUS_OFFLINE);

        meme.setUpdateTime(now);

        memeMapper.updateById(meme);



        deactivateResources(memeId);

        List<Integer> tagIds = loadTagIdsByMemeId(memeId);

        scheduleAfterCommit(() -> runAfterOffline(userId, memeId, tagIds));



        return toDeleteResponse(memeId, now);

    }



    /**

     * 恢复已下架的梗（status 3→2，重新进入审核）。

     */

    @Transactional(rollbackFor = Exception.class)

    public MemeRestoreResponseVO restoreOwnMeme(Long userId, Long memeId) {

        Meme meme = loadOwnedMeme(userId, memeId);

        if (Objects.equals(meme.getStatus(), STATUS_PURGED)) {

            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");

        }

        if (!Objects.equals(meme.getStatus(), STATUS_OFFLINE)) {

            throw new BizException(Result.CODE_BAD_REQUEST, "仅已下架的梗可恢复");

        }

        assertWithinRestoreWindow(meme.getUpdateTime());



        LocalDateTime now = LocalDateTime.now();

        meme.setStatus(STATUS_REVIEWING);

        meme.setUpdateTime(now);

        memeMapper.updateById(meme);



        reactivateResources(memeId);

        List<Integer> tagIds = loadTagIdsByMemeId(memeId);

        scheduleAfterCommit(() -> runAfterRestore(userId, memeId, tagIds));



        return toRestoreResponse(memeId, now);

    }



    /**

     * 彻底删除已下架的梗（status 3→4，从发布列表消失）。

     */

    @Transactional(rollbackFor = Exception.class)

    public MemePurgeResponseVO purgeOwnMeme(Long userId, Long memeId) {

        Meme meme = loadOwnedMeme(userId, memeId);

        if (Objects.equals(meme.getStatus(), STATUS_PURGED)) {

            return toPurgeResponse(memeId, meme.getUpdateTime());

        }

        if (!Objects.equals(meme.getStatus(), STATUS_OFFLINE)) {

            throw new BizException(Result.CODE_BAD_REQUEST, "请先下架后再彻底删除");

        }

        assertPurgeCooldown(meme.getUpdateTime());



        LocalDateTime now = LocalDateTime.now();

        meme.setStatus(STATUS_PURGED);

        meme.setUpdateTime(now);

        memeMapper.updateById(meme);



        Meme snapshot = new Meme()

                .setId(meme.getId())

                .setImage(meme.getImage());

        scheduleAfterCommit(() -> runAfterPurge(userId, memeId, snapshot));



        return toPurgeResponse(memeId, now);

    }



    private Meme loadOwnedMeme(Long userId, Long memeId) {

        if (userId == null || userId <= 0) {

            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");

        }

        if (memeId == null || memeId <= 0) {

            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");

        }



        Meme meme = memeMapper.selectById(memeId);

        if (meme == null) {

            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");

        }

        if (meme.getUserId() == null || !Objects.equals(meme.getUserId(), userId)) {

            throw new BizException(Result.CODE_FORBIDDEN, "无权操作该梗");

        }

        return meme;

    }



    private void assertWithinRestoreWindow(LocalDateTime offlineAt) {

        if (restoreDays <= 0 || offlineAt == null) {

            return;

        }

        long days = ChronoUnit.DAYS.between(offlineAt, LocalDateTime.now());

        if (days > restoreDays) {

            throw new BizException(Result.CODE_BAD_REQUEST, "已超过恢复期限（" + restoreDays + " 天）");

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



    private void runAfterOffline(Long userId, Long memeId, List<Integer> tagIds) {

        evictCaches(userId, memeId);

        try {

            memePublishAsyncHandler.decrementTagRelatedQuantity(tagIds);

        } catch (Exception e) {

            log.warn("标签计数回滚异步任务派发失败, memeId={}", memeId, e);

        }

    }



    private void runAfterRestore(Long userId, Long memeId, List<Integer> tagIds) {

        evictCaches(userId, memeId);

        try {

            memePublishAsyncHandler.incrementTagRelatedQuantity(tagIds);

        } catch (Exception e) {

            log.warn("标签计数恢复异步任务派发失败, memeId={}", memeId, e);

        }

    }



    private void runAfterPurge(Long userId, Long memeId, Meme snapshot) {

        evictCaches(userId, memeId);

        try {

            memeOssCleanupHandler.cleanupMemeAssets(snapshot);

        } catch (Exception e) {

            log.warn("OSS 清理异步任务派发失败, memeId={}", memeId, e);

        }

    }



    private void evictCaches(Long userId, Long memeId) {

        try {

            memeService.evictMemeDetailCache(memeId);

        } catch (Exception e) {

            log.warn("清理梗详情缓存失败, memeId={}", memeId, e);

        }

        try {

            userProfileService.evictUserMemesCache(userId);

        } catch (Exception e) {

            log.warn("清理用户发布列表缓存失败, userId={}", userId, e);

        }

    }



    private MemeDeleteResponseVO toDeleteResponse(Long memeId, LocalDateTime deletedAt) {

        MemeDeleteResponseVO vo = new MemeDeleteResponseVO();

        vo.setMemeId(memeId);

        vo.setStatus(STATUS_OFFLINE);

        vo.setStatusDesc(STATUS_DESC_DELETED);

        vo.setDeletedAt(formatTime(deletedAt));

        return vo;

    }



    private MemeRestoreResponseVO toRestoreResponse(Long memeId, LocalDateTime restoredAt) {

        MemeRestoreResponseVO vo = new MemeRestoreResponseVO();

        vo.setMemeId(memeId);

        vo.setStatus(STATUS_REVIEWING);

        vo.setStatusDesc(STATUS_DESC_REVIEWING);

        vo.setRestoredAt(formatTime(restoredAt));

        return vo;

    }



    private MemePurgeResponseVO toPurgeResponse(Long memeId, LocalDateTime purgedAt) {

        MemePurgeResponseVO vo = new MemePurgeResponseVO();

        vo.setMemeId(memeId);

        vo.setStatus(STATUS_PURGED);

        vo.setStatusDesc(STATUS_DESC_PURGED);

        vo.setPurgedAt(formatTime(purgedAt));

        return vo;

    }



    private String formatTime(LocalDateTime time) {

        LocalDateTime value = time != null ? time : LocalDateTime.now();

        return value.format(DATETIME_FORMAT);

    }

}


