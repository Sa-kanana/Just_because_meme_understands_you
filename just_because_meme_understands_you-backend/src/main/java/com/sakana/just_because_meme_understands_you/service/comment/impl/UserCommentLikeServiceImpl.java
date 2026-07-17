package com.sakana.just_because_meme_understands_you.service.comment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.DataStatusConstants;
import com.sakana.just_because_meme_understands_you.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.entity.UserCommentLike;
import com.sakana.just_because_meme_understands_you.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserCommentLikeMapper;
import com.sakana.just_because_meme_understands_you.service.comment.IUserCommentLikeService;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeBatchItemVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class UserCommentLikeServiceImpl implements IUserCommentLikeService {

    private static final int NOT_DELETED = DataStatusConstants.NOT_DELETED;
    private static final int DELETED = DataStatusConstants.DELETED;
    private static final int MAX_BATCH_SIZE = 50;

    @Resource
    private UserCommentLikeMapper userCommentLikeMapper;

    @Resource
    private MemeCommentMapper memeCommentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeCommentLikeVO addLike(Long userId, Long commentId) {
        requireUserId(userId);
        MemeComment comment = assertCommentLikeable(commentId);

        UserCommentLike existing = findLike(userId, commentId);
        if (existing != null && Objects.equals(existing.getIsDeleted(), NOT_DELETED)) {
            return toLikeVO(existing.getId(), commentId, true, resolveLikeCount(comment));
        }

        LocalDateTime now = LocalDateTime.now();
        if (existing != null && Objects.equals(existing.getIsDeleted(), DELETED)) {
            int updated = userCommentLikeMapper.update(null, new LambdaUpdateWrapper<UserCommentLike>()
                    .eq(UserCommentLike::getId, existing.getId())
                    .eq(UserCommentLike::getIsDeleted, DELETED)
                    .set(UserCommentLike::getIsDeleted, NOT_DELETED)
                    .set(UserCommentLike::getUpdateTime, now));
            if (updated > 0) {
                memeCommentMapper.incrementLikes(commentId);
                return toLikeVO(existing.getId(), commentId, true, resolveLikeCount(reloadComment(commentId)));
            }
            UserCommentLike active = findLike(userId, commentId);
            if (active != null && Objects.equals(active.getIsDeleted(), NOT_DELETED)) {
                return toLikeVO(active.getId(), commentId, true, resolveLikeCount(reloadComment(commentId)));
            }
        }

        UserCommentLike like = new UserCommentLike();
        like.setUserId(userId);
        like.setCommentId(commentId);
        like.setIsDeleted(NOT_DELETED);
        like.setCreateTime(now);
        like.setUpdateTime(now);
        try {
            userCommentLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            log.debug("评论点赞并发冲突, userId={}, commentId={}", userId, commentId);
            UserCommentLike conflict = findLike(userId, commentId);
            if (conflict != null && Objects.equals(conflict.getIsDeleted(), NOT_DELETED)) {
                return toLikeVO(conflict.getId(), commentId, true, resolveLikeCount(reloadComment(commentId)));
            }
            throw new BizException(Result.CODE_ERROR, "点赞失败，请稍后重试");
        }

        memeCommentMapper.incrementLikes(commentId);
        return toLikeVO(like.getId(), commentId, true, resolveLikeCount(reloadComment(commentId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeCommentLikeVO removeLike(Long userId, Long commentId) {
        requireUserId(userId);
        if (commentId == null || commentId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "commentId 不能为空");
        }

        UserCommentLike active = findLike(userId, commentId);
        if (active == null || !Objects.equals(active.getIsDeleted(), NOT_DELETED)) {
            return toLikeVO(null, commentId, false, resolveLikeCount(reloadComment(commentId)));
        }

        int updated = userCommentLikeMapper.update(null, new LambdaUpdateWrapper<UserCommentLike>()
                .eq(UserCommentLike::getId, active.getId())
                .eq(UserCommentLike::getIsDeleted, NOT_DELETED)
                .set(UserCommentLike::getIsDeleted, DELETED)
                .set(UserCommentLike::getUpdateTime, LocalDateTime.now()));
        if (updated <= 0) {
            return toLikeVO(null, commentId, false, resolveLikeCount(reloadComment(commentId)));
        }

        memeCommentMapper.decrementLikes(commentId);
        return toLikeVO(null, commentId, false, resolveLikeCount(reloadComment(commentId)));
    }

    @Override
    public MemeCommentLikeVO getLikeStatus(Long userId, Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "commentId 不能为空");
        }
        MemeComment comment = memeCommentMapper.selectById(commentId);
        boolean liked = false;
        if (userId != null && userId > 0) {
            UserCommentLike like = findLike(userId, commentId);
            liked = like != null && Objects.equals(like.getIsDeleted(), NOT_DELETED);
        }
        return toLikeVO(null, commentId, liked, resolveLikeCount(comment));
    }

    @Override
    public MemeCommentLikeBatchStatusVO batchLikeStatus(Long userId, String commaSeparatedCommentIds) {
        requireUserId(userId);
        List<Long> normalized = normalizeCommentIds(parseCommentIdsParam(commaSeparatedCommentIds));
        MemeCommentLikeBatchStatusVO result = new MemeCommentLikeBatchStatusVO();
        if (normalized.isEmpty()) {
            return result;
        }
        Set<Long> likedSet = findLikedCommentIds(userId, normalized);
        List<MemeCommentLikeBatchItemVO> items = new ArrayList<>(normalized.size());
        for (Long id : normalized) {
            MemeCommentLikeBatchItemVO item = new MemeCommentLikeBatchItemVO();
            item.setCommentId(id);
            item.setLiked(likedSet.contains(id));
            items.add(item);
        }
        result.setItems(items);
        return result;
    }

    @Override
    public Set<Long> findLikedCommentIds(Long userId, Collection<Long> commentIds) {
        if (userId == null || userId <= 0 || commentIds == null || commentIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> normalized = normalizeCommentIds(new ArrayList<>(commentIds));
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> liked = userCommentLikeMapper.selectLikedCommentIds(userId, normalized);
        if (liked == null || liked.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(liked);
    }

    private MemeComment assertCommentLikeable(Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "commentId 不能为空");
        }
        MemeComment comment = memeCommentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getIsDeleted(), NOT_DELETED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "评论不存在或已删除");
        }
        return comment;
    }

    private UserCommentLike findLike(Long userId, Long commentId) {
        return userCommentLikeMapper.selectOne(new LambdaQueryWrapper<UserCommentLike>()
                .eq(UserCommentLike::getUserId, userId)
                .eq(UserCommentLike::getCommentId, commentId)
                .last("LIMIT 1"));
    }

    private MemeComment reloadComment(Long commentId) {
        return memeCommentMapper.selectById(commentId);
    }

    private int resolveLikeCount(MemeComment comment) {
        if (comment == null || comment.getLikes() == null) {
            return 0;
        }
        return Math.max(0, comment.getLikes());
    }

    private MemeCommentLikeVO toLikeVO(Long likeId, Long commentId, boolean liked, int likeCount) {
        MemeCommentLikeVO vo = new MemeCommentLikeVO();
        vo.setLikeId(likeId);
        vo.setCommentId(commentId);
        vo.setLiked(liked);
        vo.setLikeCount(Math.max(0, likeCount));
        return vo;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    static List<Long> parseCommentIdsParam(String raw) {
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

    private List<Long> normalizeCommentIds(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new HashSet<>();
        List<Long> ordered = new ArrayList<>();
        for (Long commentId : commentIds) {
            if (commentId == null || commentId <= 0 || !unique.add(commentId)) {
                continue;
            }
            ordered.add(commentId);
            if (ordered.size() >= MAX_BATCH_SIZE) {
                break;
            }
        }
        return ordered;
    }
}
