package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserMemeItemVO {

    private Long id;

    private String name;

    private String image;

    private Integer pageViews;

    private Integer likes;

    private Integer comments;

    private LocalDateTime releaseTime;
}
