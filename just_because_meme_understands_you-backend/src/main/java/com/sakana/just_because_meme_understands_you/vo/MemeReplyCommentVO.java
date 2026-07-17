package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MemeReplyCommentVO {

    private Long id;

    private Long parentId;

    private Long userId;

    private String userName;

    private String userAvatar;

    private Long replyToUserId;

    private String replyToUserName;

    private String content;

    private List<String> images;

    private Integer likes;

    private Boolean liked;

    private Boolean owner;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
