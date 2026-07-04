package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.UserFavorite;
import com.sakana.just_because_meme_understands_you.mapper.UserFavoriteMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeBloomFilterService;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteService;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.service.user.UserFavoriteCountService;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserFavoriteServiceImpl implements IUserFavoriteService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Resource
    private IMemeService memeService;

    @Resource
    private MemeBloomFilterService memeBloomFilterService;

    @Resource
    private UserFavoriteCountService userFavoriteCountService;

    @Resource
    private IUserProfileService userProfileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeFavoriteVO addFavorite(Long userId, MemeFavoriteRequestDTO request) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        if (request == null || request.getMemeId() == null || request.getMemeId() <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        long memeId = request.getMemeId();
        long folderId = request.getFolderId() != null ? request.getFolderId() : 0L;
        if (folderId < 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
        }

        // 布隆过滤器快速预判梗存在性，false 则一定不存在
        if (!memeBloomFilterService.mightContain((int) memeId)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可收藏");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null || meme.getStatus() == null || meme.getStatus() != 1) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可收藏");
        }

        UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
        if (active != null) {
            return toVO(active);
        }

        LocalDateTime now = LocalDateTime.now();
        UserFavorite deleted = findFavorite(userId, memeId, DELETED);
        if (deleted != null) {
            deleted.setIsDeleted(NOT_DELETED);
            deleted.setFolderId(folderId);
            deleted.setUpdateTime(now);
            userFavoriteMapper.updateById(deleted);
            userFavoriteCountService.increment(userId);
            userProfileService.evictUserCache(userId);
            return toVO(deleted);
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setMemeId(memeId);
        favorite.setFolderId(folderId);
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

        userFavoriteCountService.increment(userId);
        userProfileService.evictUserCache(userId);
        return toVO(favorite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long memeId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }

        UserFavorite active = findFavorite(userId, memeId, NOT_DELETED);
        if (active == null) {
            return;
        }

        LambdaUpdateWrapper<UserFavorite> updateWrapper = new LambdaUpdateWrapper<UserFavorite>()
                .eq(UserFavorite::getId, active.getId())
                .eq(UserFavorite::getIsDeleted, NOT_DELETED)
                .set(UserFavorite::getIsDeleted, DELETED)
                .set(UserFavorite::getUpdateTime, LocalDateTime.now());
        int updated = userFavoriteMapper.update(null, updateWrapper);
        if (updated <= 0) {
            return;
        }

        userFavoriteCountService.decrement(userId);
        userProfileService.evictUserCache(userId);
    }

    @Override
    public boolean isFavorited(Long userId, Long memeId) {
        if (userId == null || userId <= 0 || memeId == null || memeId <= 0) {
            return false;
        }
        return findFavorite(userId, memeId, NOT_DELETED) != null;
    }

    private UserFavorite findFavorite(Long userId, Long memeId, int isDeleted) {
        return userFavoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getMemeId, memeId)
                        .eq(UserFavorite::getIsDeleted, isDeleted)
                        .last("LIMIT 1")
        );
    }

    private MemeFavoriteVO toVO(UserFavorite favorite) {
        MemeFavoriteVO vo = new MemeFavoriteVO();
        vo.setFavoriteId(favorite.getId());
        vo.setCreateTime(favorite.getCreateTime());
        vo.setUpdateTime(favorite.getUpdateTime());
        return vo;
    }
}
