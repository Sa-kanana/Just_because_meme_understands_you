package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

@Data
public class MemeViewReportRequestDTO {

    private Long memeId;

    /** detail / list / search / share，默认 detail */
    private String source;
}
