package com.sakana.just_because_meme_understands_you.api.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 梗关联的标签。 */
    private List<AdminMemeTagVO> tags;

    /** 梗关联的全部资源，包含已下架或归档记录。 */
    private List<AdminMemeResourceVO> resources;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime releaseTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private String offlineReason;

    private Integer appealRejectCount;
}
