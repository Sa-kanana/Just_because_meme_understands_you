package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatMessagePageVO {

    private List<AiChatMessageVO> list = new ArrayList<>();

    private Integer page;

    private Integer size;

    private Long total;

    private Boolean hasMore;
}
