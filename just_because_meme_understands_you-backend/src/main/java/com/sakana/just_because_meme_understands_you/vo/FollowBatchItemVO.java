package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 批量关注状态项（首页作者卡）。
 */
@Data
public class FollowBatchItemVO {

    private String userId;

    private Boolean followed;
}
