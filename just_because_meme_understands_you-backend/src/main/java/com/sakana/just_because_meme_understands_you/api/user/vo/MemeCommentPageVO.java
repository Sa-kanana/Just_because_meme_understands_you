package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class MemeCommentPageVO {

    private List<MemeRootCommentVO> list;

    private Long total;

    private Boolean hasMore;
}
