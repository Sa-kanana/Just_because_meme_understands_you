package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 梗的详细信息（接口 Meme）
 */
@Data
public class MemeDetailVO {

    /**
     * id 唯一标识
     */
    private Integer id;

    /**
     * 介绍
     */
    private String introduction;

    /**
     * 名字
     */
    private String name;

    /**
     * 图片 url
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
    private LocalDateTime releaseTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 状态（1.正常，2.审核中，3.下架）
     */
    private Integer status;

    /**
     * 标签列表，对应接口 Meme.memeTag
     */
    private List<MemeTagVO> memeTag;

    /**
     * 相关链接列表，对应接口 Meme.links
     */
    private List<MemeResourceVO> links;
}

