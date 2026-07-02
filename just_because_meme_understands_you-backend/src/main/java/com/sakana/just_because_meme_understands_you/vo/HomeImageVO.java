package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播图展示 VO，字段与接口 DataResponse.data[] 一致（snake_case）
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class HomeImageVO {

    private String title;
    private String imgUrl;
    /** 跳转类型：0-无跳转，1-内部文章/梗ID，2-外部链接 */
    private Integer targetType;
    private String targetValue;
    private Integer sortOrder;
    /** 状态：1-上线，0-下线 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
