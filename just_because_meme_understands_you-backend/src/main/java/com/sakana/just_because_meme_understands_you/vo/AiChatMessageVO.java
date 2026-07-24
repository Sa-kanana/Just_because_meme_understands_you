package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiChatMessageVO {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String requestId;

    private Integer tokenEstimate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
