package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class HomeHotTagVO {

    private Integer id;

    private String name;

    private Integer memeCount;

    private Integer heatScore;
}
