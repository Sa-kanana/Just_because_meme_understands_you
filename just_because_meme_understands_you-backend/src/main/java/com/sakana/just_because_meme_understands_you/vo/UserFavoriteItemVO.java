package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFavoriteItemVO {

    private Long id;

    /** 收藏记录 id，用于排序/移动 */
    private Long favoriteId;

    private String name;

    private String image;

    private Integer pageViews;

    private Long folderId;

    private Integer sortOrder;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime favoriteTime;
}
