package com.sakana.just_because_meme_understands_you.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminHomeImageVO {

    private Long id;
    private String title;
    private String imgUrl;
    private Integer targetType;
    private String targetValue;
    private Integer sortOrder;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
