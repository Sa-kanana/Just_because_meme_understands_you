package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 梗分页列表展示视图对象
 */
@Data
public class MemeListItemVO {

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
     * 发布者（列表场景不含 signature）
     */
    private AuthorVO author;

    /**
     * 接口返回的标签列表（memeTag）
     */
    private List<MemeTagVO> memeTag;

    /**
     * 兼容旧字段：标签列表（与 memeTag 同形，避免回传实体）
     */
    private List<MemeTagVO> label;
}

