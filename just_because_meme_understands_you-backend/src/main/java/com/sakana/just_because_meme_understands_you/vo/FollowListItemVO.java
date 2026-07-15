package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class FollowListItemVO {

    private String userId;

    private String nickname;

    private String avatar;

    private String signature;

    private String followTime;

    /** 当前登录用户是否已关注该用户 */
    private Boolean followedByMe;

    /** 当前登录用户与该用户是否互关 */
    private Boolean mutual;
}
