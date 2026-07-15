package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

@Data
public class FollowListPageVO {

    private List<FollowListItemVO> list;

    private Integer page;

    private Integer size;

    private Long total;

    private Boolean hasMore;
}
