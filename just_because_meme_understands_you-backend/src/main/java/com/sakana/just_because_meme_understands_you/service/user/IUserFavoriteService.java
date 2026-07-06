package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.dto.FavoriteMoveDTO;
import com.sakana.just_because_meme_understands_you.dto.FavoriteReorderDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeFavoriteRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteMoveResultVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteStatusVO;
import com.sakana.just_because_meme_understands_you.vo.MemeFavoriteVO;

public interface IUserFavoriteService {

    MemeFavoriteVO addFavorite(Long userId, MemeFavoriteRequestDTO request);

    /**
     * 单条收藏移动到目标夹（按 memeId 定位收藏记录）。
     */
    MemeFavoriteVO moveFavorite(Long userId, Long memeId, Long targetFolderId);

    /**
     * 批量移动收藏到目标夹。
     */
    FavoriteMoveResultVO batchMoveFavorites(Long userId, FavoriteMoveDTO request);

    /**
     * 夹内收藏排序：按 favoriteIds 顺序重写 sort_order。
     */
    void reorderFavorites(Long userId, FavoriteReorderDTO request);

    void removeFavorite(Long userId, Long memeId);

    MemeFavoriteStatusVO getFavoriteStatus(Long userId, Long memeId);

    boolean isFavorited(Long userId, Long memeId);
}
