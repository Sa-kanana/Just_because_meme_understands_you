package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

@Data
public class MemeCommentPageVO {

    private List<MemeRootCommentVO> list;

    private Long total;

    private Boolean hasMore;
}
