package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 关注操作 / 单人关注状态响应。
 */
@Data
public class FollowedVO {

    private String userId;

    private Boolean followed;

    /** 目标用户关注数 */
    private Integer followCount;

    /** 目标用户粉丝数 */
    private Integer fansCount;

    /** 是否互相关注 */
    private Boolean mutual;
}
