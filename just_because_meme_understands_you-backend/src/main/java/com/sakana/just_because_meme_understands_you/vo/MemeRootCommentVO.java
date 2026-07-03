package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MemeRootCommentVO {

    private Long id;

    private Long userId;

    private String userName;

    private String userAvatar;

    private String content;

    private List<String> images;

    private Integer replyCount;

    private Integer likes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
