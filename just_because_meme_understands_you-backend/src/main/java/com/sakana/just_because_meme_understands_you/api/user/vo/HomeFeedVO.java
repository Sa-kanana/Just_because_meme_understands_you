package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class HomeFeedVO {

    private String sort;

    private List<MemeListItemVO> list;

    private Integer page;

    private Integer size;

    private Long total;

    private Boolean hasMore;
}
