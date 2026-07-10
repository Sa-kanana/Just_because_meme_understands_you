package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeLikeRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.UserLike;
import com.sakana.just_because_meme_understands_you.entity.UserStats;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserLikeMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserStatsMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeBloomFilterService;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.service.user.IUserLikeService;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeBatchItemVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class UserLikeServiceImpl implements IUserLikeService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int MAX_BATCH_SIZE = 50;

    @Resource
    private UserLikeMapper userLikeMapper;

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private IMemeService memeService;

    @Resource
    private MemeBloomFilterService memeBloomFilterService;

    @Resource
    private UserStatsMapper userStatsMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeLikeVO addLike(Long userId, MemeLikeRequestDTO request) {
        requireUserId(userId);
        long memeId = parseMemeId(request);
        Meme meme = assertMemeLikeable(memeId);

        UserLike active = findLike(userId, memeId, NOT_DELETED);
        if (active != null) {
            return toLikeVO(active, meme, true);
        }

        LocalDateTime now = LocalDateTime.now();
        UserLike deleted = findLike(userId, memeId, DELETED);
        if (deleted != null) {
            int updated = userLikeMapper.update(null, new LambdaUpdateWrapper<UserLike>()
                    .eq(UserLike::getId, deleted.getId())
                    .eq(UserLike::getIsDeleted, DELETED)
                    .set(UserLike::getIsDeleted, NOT_DELETED)
                    .set(UserLike::getUpdateTime, now));
            if (updated > 0) {
                applyLikeCountDelta(meme, 1);
                deleted.setIsDeleted(NOT_DELETED);
                deleted.setUpdateTime(now);
                return toLikeVO(deleted, reloadMeme(memeId), true);
            }
            UserLike existing = findLike(userId, memeId, NOT_DELETED);
            if (existing != null) {
                return toLikeVO(existing, reloadMeme(memeId), true);
            }
        }

        UserLike like = new UserLike();
        like.setUserId(userId);
        like.setMemeId(memeId);
        like.setIsDeleted(NOT_DELETED);
        like.setCreateTime(now);
        like.setUpdateTime(now);
        try {
            userLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            log.debug("点赞并发冲突, userId={}, memeId={}", userId, memeId);
            UserLike existing = findLike(userId, memeId, NOT_DELETED);
            if (existing != null) {
                return toLikeVO(existing, reloadMeme(memeId), true);
            }
            throw new BizException(Result.CODE_ERROR, "点赞失败，请稍后重试");
        }

        applyLikeCountDelta(meme, 1);
        return toLikeVO(like, reloadMeme(memeId), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeLikeDeleteVO removeLike(Long userId, Long memeId) {
        requireUserId(userId);
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }

        Meme meme = memeService.getById(memeId);
        UserLike active = findLike(userId, memeId, NOT_DELETED);
        if (active == null) {
            return toDeleteVO(memeId, false, resolveLikeCount(meme));
        }

        int updated = userLikeMapper.update(null, new LambdaUpdateWrapper<UserLike>()
                .eq(UserLike::getId, active.getId())
                .eq(UserLike::getIsDeleted, NOT_DELETED)
                .set(UserLike::getIsDeleted, DELETED)
                .set(UserLike::getUpdateTime, LocalDateTime.now()));
        if (updated <= 0) {
            return toDeleteVO(memeId, false, resolveLikeCount(reloadMeme(memeId)));
        }

        if (meme != null) {
            applyLikeCountDelta(meme, -1);
        } else {
            memeMapper.decrementLikes(memeId);
        }
        return toDeleteVO(memeId, false, resolveLikeCount(reloadMeme(memeId)));
    }

    @Override
    public MemeLikeStatusVO getLikeStatus(Long userId, Long memeId) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        Meme meme = memeService.getById(memeId);
        MemeLikeStatusVO vo = new MemeLikeStatusVO();
        vo.setLikeCount(resolveLikeCount(meme));
        vo.setLiked(userId != null && userId > 0 && findLike(userId, memeId, NOT_DELETED) != null);
        return vo;
    }

    @Override
    public MemeLikeBatchStatusVO batchLikeStatus(Long userId, String commaSeparatedMemeIds) {
        return batchLikeStatus(userId, parseMemeIdsParam(commaSeparatedMemeIds));
    }

    @Override
    public MemeLikeBatchStatusVO batchLikeStatus(Long userId, List<Long> memeIds) {
        requireUserId(userId);
        List<Long> normalized = normalizeMemeIds(memeIds);
        MemeLikeBatchStatusVO result = new MemeLikeBatchStatusVO();
        if (normalized.isEmpty()) {
            return result;
        }

        Set<Long> likedSet = new HashSet<>(userLikeMapper.selectLikedMemeIds(userId, normalized));
        List<MemeLikeBatchItemVO> items = new ArrayList<>(normalized.size());
        for (Long memeId : normalized) {
            MemeLikeBatchItemVO item = new MemeLikeBatchItemVO();
            item.setMemeId(memeId);
            item.setLiked(likedSet.contains(memeId));
            items.add(item);
        }
        result.setItems(items);
        return result;
    }

    @Override
    public boolean isLiked(Long userId, Long memeId) {
        if (userId == null || userId <= 0 || memeId == null || memeId <= 0) {
            return false;
        }
        return findLike(userId, memeId, NOT_DELETED) != null;
    }

    static List<Long> parseMemeIdsParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<Long> ids = new ArrayList<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(trimmed);
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid token
            }
        }
        return ids;
    }

    private List<Long> normalizeMemeIds(List<Long> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new HashSet<>();
        List<Long> ordered = new ArrayList<>();
        for (Long memeId : memeIds) {
            if (memeId == null || memeId <= 0 || !unique.add(memeId)) {
                continue;
            }
            ordered.add(memeId);
            if (ordered.size() >= MAX_BATCH_SIZE) {
                break;
            }
        }
        return ordered;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private long parseMemeId(MemeLikeRequestDTO request) {
        if (request == null || request.getMemeId() == null || request.getMemeId() <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        return request.getMemeId();
    }

    private Meme assertMemeLikeable(long memeId) {
        if (!memeBloomFilterService.mightContain((int) memeId)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可点赞");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null || meme.getStatus() == null || meme.getStatus() != MemeVisibilitySupport.STATUS_NORMAL) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可点赞");
        }
        return meme;
    }

    private UserLike findLike(Long userId, Long memeId, int deletedFlag) {
        return userLikeMapper.selectOne(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getMemeId, memeId)
                .eq(UserLike::getIsDeleted, deletedFlag)
                .last("LIMIT 1"));
    }

    private void applyLikeCountDelta(Meme meme, int delta) {
        if (meme == null || meme.getId() == null) {
            return;
        }
        long memeId = meme.getId().longValue();
        if (delta > 0) {
            memeMapper.incrementLikes(memeId);
        } else if (delta < 0) {
            memeMapper.decrementLikes(memeId);
        }
        adjustAuthorLikeReceived(meme.getUserId(), delta);
    }

    private void adjustAuthorLikeReceived(Long authorUserId, int delta) {
        if (authorUserId == null || authorUserId <= 0 || delta == 0) {
            return;
        }
        UserStats stats = userStatsMapper.selectById(authorUserId);
        if (stats == null) {
            stats = new UserStats();
            stats.setUserId(authorUserId);
            stats.setMemeCount(0);
            stats.setLikeReceived(Math.max(0, delta));
            stats.setFollowCount(0);
            stats.setFansCount(0);
            stats.setFavoriteCount(0);
            userStatsMapper.insert(stats);
            return;
        }
        int current = stats.getLikeReceived() == null ? 0 : stats.getLikeReceived();
        stats.setLikeReceived(Math.max(0, current + delta));
        userStatsMapper.updateById(stats);
    }

    private Meme reloadMeme(long memeId) {
        Meme meme = memeMapper.selectById(memeId);
        if (meme != null) {
            return meme;
        }
        return memeService.getById(memeId);
    }

    private int resolveLikeCount(Meme meme) {
        if (meme == null || meme.getLikes() == null) {
            return 0;
        }
        return Math.max(0, meme.getLikes());
    }

    private MemeLikeVO toLikeVO(UserLike like, Meme meme, boolean liked) {
        MemeLikeVO vo = new MemeLikeVO();
        vo.setLikeId(like.getId());
        vo.setMemeId(like.getMemeId());
        vo.setLiked(liked);
        vo.setLikeCount(resolveLikeCount(meme));
        vo.setCreateTime(like.getCreateTime());
        return vo;
    }

    private MemeLikeDeleteVO toDeleteVO(Long memeId, boolean liked, int likeCount) {
        MemeLikeDeleteVO vo = new MemeLikeDeleteVO();
        vo.setMemeId(memeId);
        vo.setLiked(liked);
        vo.setLikeCount(Math.max(0, likeCount));
        return vo;
    }
}
