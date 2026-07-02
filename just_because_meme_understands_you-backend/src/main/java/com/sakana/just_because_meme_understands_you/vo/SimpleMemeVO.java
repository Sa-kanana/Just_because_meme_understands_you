package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口 SimpleMeme：关键字搜索返回的梗项
 */
@Data
public class SimpleMemeVO {

    /**
     * id唯一标识
     */
    private Integer id;

    /**
     * 名字
     */
    private String name;

    /**
     * 图片url
     */
    private String image;

    /**
     * 浏览量
     */
    private Integer pageViews;

    /**
     * 点赞数
     */
    private Integer likes;

    /**
     * 评论数
     */
    private Integer comments;

    /**
     * 发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime releaseTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 状态（1.正常，2.审核中，3.下架）
     */
    private Integer status;

    /**
     * 标签
     */
    private List<MemeTagVO> memeTag;
}
