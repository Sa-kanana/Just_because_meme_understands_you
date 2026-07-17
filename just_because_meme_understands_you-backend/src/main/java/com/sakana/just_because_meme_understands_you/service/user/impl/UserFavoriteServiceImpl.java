package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AfterCommitExecutor;
import com.sakana.just_because_meme_understands_you.dto.FavoriteMoveDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeBloomFilterService;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteService;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.service.user.UserFavoriteCountService;
import com.sakana.just_because_meme_understands_you.vo.FavoriteMoveResultVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserFavoriteServiceImpl implements IUserFavoriteService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int SORT_STEP = 1000;

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Resource
    private IMemeService memeService;

    @Resource
    private MemeBloomFilterService memeBloomFilterService;

    @Resource
    private AfterCommitExecutor afterCommitExecutor;

    @Resource
    private UserFavoriteCountService userFavoriteCountService;

    @Resource
    private IUserProfileService userProfileService;

    @Resource
    private IUserFavoriteFolderService userFavoriteFolderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeFavoriteVO addFavorite(Long userId, MemeFavoriteRequestDTO request) {
        requireUserId(userId);
        if (request == null || request.getMemeId() == null || request.getMemeId() <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        long memeId = request.getMemeId();
        long folderId = request.getFolderId() != null ? request.getFolderId() : IUserFavoriteFolderService.DEFAULT_FOLDER_ID;
        if (folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        userFavoriteFolderService.assertFolderOwnedByUser(userId, folderId);
        assertMemeCollectible(memeId);

        UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
        if (active != null) {
            long currentFolder = active.getFolderId() == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : active.getFolderId();
            if (currentFolder == folderId) {
                return toVO(active);
            }
            return moveFavoriteInternal(userId, active, folderId, true);
        }

        LocalDateTime now = LocalDateTime.now();
        UserFavorite deleted = findFavorite(userId, memeId, DELETED);
        if (deleted != null) {
            deleted.setIsDeleted(NOT_DELETED);
            deleted.setFolderId(folderId);
            deleted.setSortOrder(nextTopSortOrder(userId, folderId));
            deleted.setUpdateTime(now);
            userFavoriteMapper.updateById(deleted);
            userFavoriteFolderService.adjustMemeCount(folderId, 1);
            userFavoriteCountService.increment(userId);
            evictCaches(userId);
            return toVO(deleted);
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setMemeId(memeId);
        favorite.setFolderId(folderId);
        favorite.setSortOrder(nextTopSortOrder(userId, folderId));
        favorite.setIsDeleted(NOT_DELETED);
        favorite.setCreateTime(now);
        favorite.setUpdateTime(now);

        try {
            userFavoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            log.debug("收藏并发冲突, userId={}, memeId={}", userId, memeId);
            UserFavorite existing = findFavorite(userId, memeId, NOT_DELETED);
            if (existing != null) {
                return toVO(existing);
            }
            throw new BizException(Result.CODE_ERROR, "收藏失败，请稍后重试");
        }

        userFavoriteFolderService.adjustMemeCount(folderId, 1);
        userFavoriteCountService.increment(userId);
        evictCaches(userId);
        return toVO(favorite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeFavoriteVO moveFavorite(Long userId, Long memeId, Long targetFolderId) {
        requireUserId(userId);
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        if (targetFolderId == null || targetFolderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        userFavoriteFolderService.assertFolderOwnedByUser(userId, targetFolderId);
        UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
        if (active == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "尚未收藏该梗");
        }
        return moveFavoriteInternal(userId, active, targetFolderId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteMoveResultVO batchMoveFavorites(Long userId, FavoriteMoveDTO request) {
        requireUserId(userId);
        if (request == null || request.getTargetFolderId() == null || request.getTargetFolderId() < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "targetFolderId 不合法");
        }
        if (request.getMemeIds() == null || request.getMemeIds().isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeIds 不能为空");
        }
        long targetFolderId = request.getTargetFolderId();
        userFavoriteFolderService.assertFolderOwnedByUser(userId, targetFolderId);

        FavoriteMoveResultVO result = new FavoriteMoveResultVO();
        int moved = 0;
        for (Long memeId : request.getMemeIds()) {
            if (memeId == null || memeId <= 0) {
                continue;
            }
            UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
            if (active == null) {
                result.getFailed().add(memeId);
                continue;
            }
            long sourceFolder = active.getFolderId() == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : active.getFolderId();
            if (sourceFolder == targetFolderId) {
                moved++;
                continue;
            }
            active.setFolderId(targetFolderId);
            active.setSortOrder(nextTopSortOrder(userId, targetFolderId));
            active.setUpdateTime(LocalDateTime.now());
            userFavoriteMapper.updateById(active);
            userFavoriteFolderService.adjustMemeCount(sourceFolder, -1);
            userFavoriteFolderService.adjustMemeCount(targetFolderId, 1);
            moved++;
        }
        result.setMovedCount(moved);
        evictCaches(userId);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderFavorites(Long userId, FavoriteReorderDTO request) {
        requireUserId(userId);
        if (request == null || request.getFolderId() == null || request.getFolderId() < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }
        userFavoriteFolderService.assertFolderOwnedByUser(userId, request.getFolderId());
        if (request.getFavoriteIds() == null || request.getFavoriteIds().isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "排序项不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (Long favId : request.getFavoriteIds()) {
            if (favId == null || favId <= 0) {
                continue;
            }
            int updated = userFavoriteMapper.update(null, new LambdaUpdateWrapper<UserFavorite>()
                    .eq(UserFavorite::getId, favId)
                    .eq(UserFavorite::getUserId, userId)
                    .eq(UserFavorite::getFolderId, request.getFolderId())
                    .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                    .set(UserFavorite::getSortOrder, order)
                    .set(UserFavorite::getUpdateTime, now));
            if (updated <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "收藏记录不在该夹中: " + favId);
            }
            order += SORT_STEP;
        }
        evictCaches(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long memeId) {
        requireUserId(userId);
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
        if (active == null) {
            return;
        }
        int updated = userFavoriteMapper.update(null, new LambdaUpdateWrapper<UserFavorite>()
                .eq(UserFavorite::getId, active.getId())
                .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                .set(UserFavorite::getIsDeleted, DELETED)
                .set(UserFavorite::getUpdateTime, LocalDateTime.now()));
        if (updated <= 0) {
            return;
        }
        long folderId = active.getFolderId() == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : active.getFolderId();
        userFavoriteFolderService.adjustMemeCount(folderId, -1);
        userFavoriteCountService.decrement(userId);
        evictCaches(userId);
    }

    @Override
    public MemeFavoriteStatusVO getFavoriteStatus(Long userId, Long memeId) {
        MemeFavoriteStatusVO vo = new MemeFavoriteStatusVO();
        if (userId == null || userId <= 0 || memeId == null || memeId <= 0) {
            vo.setFavorited(false);
            return vo;
        }
        UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
        if (active == null) {
            vo.setFavorited(false);
            return vo;
        }
        vo.setFavorited(true);
        vo.setFolderId(active.getFolderId());
        vo.setSortOrder(active.getSortOrder());
        return vo;
    }

    @Override
    public boolean isFavorited(Long userId, Long memeId) {
        if (userId == null || userId <= 0 || memeId == null || memeId <= 0) {
            return false;
        }
        return findFavorite(userId, memeId, NOT_DELETED) != null;
    }

    // ==================== private ====================

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private void assertMemeCollectible(long memeId) {
        if (!memeBloomFilterService.mightContain((int) memeId)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可收藏");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null || meme.getStatus() == null || meme.getStatus() != 1) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可收藏");
        }
    }

    private MemeFavoriteVO moveFavoriteInternal(Long userId, UserFavorite active, long targetFolderId, boolean evict) {
        long sourceFolder = active.getFolderId() == null ? IUserFavoriteFolderService.DEFAULT_FOLDER_ID : active.getFolderId();
        if (sourceFolder == targetFolderId) {
            return toVO(active);
        }
        active.setFolderId(targetFolderId);
        active.setSortOrder(nextTopSortOrder(userId, targetFolderId));
        active.setUpdateTime(LocalDateTime.now());
        userFavoriteMapper.updateById(active);
        userFavoriteFolderService.adjustMemeCount(sourceFolder, -1);
        userFavoriteFolderService.adjustMemeCount(targetFolderId, 1);
        if (evict) {
            evictCaches(userId);
        }
        return toVO(active);
    }

    private int nextTopSortOrder(Long userId, long folderId) {
        UserFavorite top = userFavoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getFolderId, folderId)
                        .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                        .orderByAsc(UserFavorite::getSortOrder)
                        .last("LIMIT 1"));
        if (top == null || top.getSortOrder() == null) {
            return SORT_STEP;
        }
        return top.getSortOrder() - SORT_STEP;
    }

    private UserFavorite findFavorite(Long userId, Long memeId, int isDeleted) {
        return userFavoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getMemeId, memeId)
                        .eq(UserFavorite::getIsDeleted, isDeleted)
                        .last("LIMIT 1"));
    }

    private MemeFavoriteVO toVO(UserFavorite favorite) {
        MemeFavoriteVO vo = new MemeFavoriteVO();
        vo.setFavoriteId(favorite.getId());
        vo.setFolderId(favorite.getFolderId());
        vo.setSortOrder(favorite.getSortOrder());
        vo.setCreateTime(favorite.getCreateTime());
        vo.setUpdateTime(favorite.getUpdateTime());
        return vo;
    }

    private void evictCaches(Long userId) {
        // Folder cache itself schedules after-commit delete; profile cache deletes sync.
        // AfterCommitExecutor allows nested after-commit calls to run immediately.
        afterCommitExecutor.execute(() -> {
            userFavoriteFolderService.evictFolderCache(userId);
            userProfileService.evictUserCache(userId);
        });
    }
}
