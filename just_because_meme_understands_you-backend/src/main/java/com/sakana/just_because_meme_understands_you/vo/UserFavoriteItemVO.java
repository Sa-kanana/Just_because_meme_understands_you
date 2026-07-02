package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class UserFavoriteItemVO {

    private Long id;

    private String name;

    private String image;

    private Integer pageViews;
}
