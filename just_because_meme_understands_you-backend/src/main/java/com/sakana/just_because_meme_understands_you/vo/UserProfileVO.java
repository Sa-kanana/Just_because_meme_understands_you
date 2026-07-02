package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileVO {

    private Long userId;

    private String nickname;

    private String avatar;

    private String signature;

    private Integer gender;

    private Boolean isSelf;

    private Boolean isFollow;

    private UserProfileStatsVO stats;

    private List<UserMemeItemVO> memes;

    private List<UserFavoriteItemVO> favorites;
}
