package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class UserProfileStatsVO {

    private Integer followCount;

    private Integer fansCount;

    private Integer memeCount;

    private Integer likeReceived;

    private Integer favoriteCount;
}
