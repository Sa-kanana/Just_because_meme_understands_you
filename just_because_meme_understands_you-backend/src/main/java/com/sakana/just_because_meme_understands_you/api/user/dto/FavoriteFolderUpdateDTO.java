package com.sakana.just_because_meme_understands_you.api.user.dto;

import lombok.Data;

@Data
public class FavoriteFolderUpdateDTO {

    private String name;

    private String description;

    private Integer isPublic;
}
