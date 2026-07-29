package com.sakana.just_because_meme_understands_you.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminMemeDetailVO {

    private Long id;
    private String name;
    private String introduction;
    private String image;
    private Integer status;
    private String statusDesc;
    private Long userId;
    private String authorNickname;
    private Integer pageViews;
    private Integer likes;
    private Integer comments;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime releaseTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private String offlineReason;

    private Integer appealRejectCount;
}
