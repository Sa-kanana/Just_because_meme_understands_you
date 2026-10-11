package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class MemeFavoriteStatusVO {

    private Boolean favorited;

    private Long folderId;

    private Integer sortOrder;
}
