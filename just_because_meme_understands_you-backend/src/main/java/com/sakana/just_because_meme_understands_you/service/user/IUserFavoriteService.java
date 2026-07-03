package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteVO;

public interface IUserFavoriteService {

    MemeFavoriteVO addFavorite(Long userId, MemeFavoriteRequestDTO request);

    void removeFavorite(Long userId, Long memeId);

    boolean isFavorited(Long userId, Long memeId);
}
