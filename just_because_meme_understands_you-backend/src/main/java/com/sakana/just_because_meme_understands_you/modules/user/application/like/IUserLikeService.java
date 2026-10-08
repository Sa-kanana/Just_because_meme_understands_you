package com.sakana.just_because_meme_understands_you.modules.user.application.like;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeLikeRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeDeleteVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeVO;

import java.util.List;

public interface IUserLikeService {

    MemeLikeVO addLike(Long userId, MemeLikeRequestDTO request);

    MemeLikeDeleteVO removeLike(Long userId, Long memeId);

    MemeLikeStatusVO getLikeStatus(Long userId, Long memeId);

    MemeLikeBatchStatusVO batchLikeStatus(Long userId, List<Long> memeIds);

    MemeLikeBatchStatusVO batchLikeStatus(Long userId, String commaSeparatedMemeIds);

    boolean isLiked(Long userId, Long memeId);
}
