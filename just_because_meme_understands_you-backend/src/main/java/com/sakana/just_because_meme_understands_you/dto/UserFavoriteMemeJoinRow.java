package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏与梗 JOIN 查询行，用于分页列表。
 */
@Data
public class UserFavoriteMemeJoinRow {

    private Long favoriteId;

    private Long memeId;

    private Long folderId;

    private Integer sortOrder;

    private LocalDateTime favoriteTime;

    private String name;

    private String image;

    private Integer pageViews;
}
