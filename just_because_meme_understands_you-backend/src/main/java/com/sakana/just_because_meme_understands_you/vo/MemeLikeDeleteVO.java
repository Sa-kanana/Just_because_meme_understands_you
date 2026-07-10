package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class MemeLikeDeleteVO {

    private Long memeId;

    private Boolean liked;

    private Integer likeCount;
}
