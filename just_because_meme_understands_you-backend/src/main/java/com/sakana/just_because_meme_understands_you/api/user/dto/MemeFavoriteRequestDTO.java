package com.sakana.just_because_meme_understands_you.api.user.dto;

import lombok.Data;

@Data
public class MemeFavoriteRequestDTO {

    private Long memeId;

    private Long folderId;
}
