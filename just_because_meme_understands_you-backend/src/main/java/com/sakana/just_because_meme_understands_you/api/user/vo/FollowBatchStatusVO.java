package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class FollowBatchStatusVO {

    private List<FollowBatchItemVO> items;
}
