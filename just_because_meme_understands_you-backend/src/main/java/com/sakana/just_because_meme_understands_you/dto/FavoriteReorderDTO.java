package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

import java.util.List;

/**
 * 夹内收藏排序：按 favoriteIds 顺序重写 sort_order。
 */
@Data
public class FavoriteReorderDTO {

    private Long folderId;

    private List<Long> favoriteIds;
}
