package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

@Data
public class FavoriteFolderCreateDTO {

    private String name;

    private String description;

    /** 0 私密 / 1 公开，默认 1 */
    private Integer isPublic;
}
