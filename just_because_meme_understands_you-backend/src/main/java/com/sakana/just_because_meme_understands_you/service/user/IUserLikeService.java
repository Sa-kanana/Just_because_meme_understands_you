package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.dto.MemeLikeRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeLikeVO;

import java.util.List;

public interface IUserLikeService {

    MemeLikeVO addLike(Long userId, MemeLikeRequestDTO request);

    MemeLikeDeleteVO removeLike(Long userId, Long memeId);

    MemeLikeStatusVO getLikeStatus(Long userId, Long memeId);

    MemeLikeBatchStatusVO batchLikeStatus(Long userId, List<Long> memeIds);

    MemeLikeBatchStatusVO batchLikeStatus(Long userId, String commaSeparatedMemeIds);

    boolean isLiked(Long userId, Long memeId);
}
