package com.sakana.just_because_meme_understands_you.api.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class MemeCommentCreateRequestDTO {

    private Long memeId;

    private String rootId;

    private String parentId;

    private String content;

    private List<String> imageUrls;
}
