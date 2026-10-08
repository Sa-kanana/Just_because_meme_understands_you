package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

@Data
public class MemeCommentLikeBatchItemVO {

    private Long commentId;

    private Boolean liked;
}
