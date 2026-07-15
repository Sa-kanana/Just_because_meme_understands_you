package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

@Data
public class FollowBatchStatusVO {

    private List<FollowBatchItemVO> items;
}
