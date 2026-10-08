package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MemeCommentLikeBatchStatusVO {

    private List<MemeCommentLikeBatchItemVO> items = new ArrayList<>();
}
