package com.sakana.just_because_meme_understands_you.service.comment;

import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentLikeVO;

import java.util.Collection;
import java.util.Set;

public interface IUserCommentLikeService {

    MemeCommentLikeVO addLike(Long userId, Long commentId);

    MemeCommentLikeVO removeLike(Long userId, Long commentId);

    MemeCommentLikeVO getLikeStatus(Long userId, Long commentId);

    MemeCommentLikeBatchStatusVO batchLikeStatus(Long userId, String commaSeparatedCommentIds);

    Set<Long> findLikedCommentIds(Long userId, Collection<Long> commentIds);
}
