package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageVO<T> {

    private List<T> list;

    private Integer page;

    private Integer size;

    private Long total;
}
