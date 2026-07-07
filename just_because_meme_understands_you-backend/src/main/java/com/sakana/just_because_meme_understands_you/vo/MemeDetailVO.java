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
     * 状态（1.正常，2.审核中，3.下架，4.已彻底删除）
     */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 当前登录用户是否为发布者 */
    private Boolean owner;

    /** 是否为发布者预览模式（审核中/已下架，仅本人可见） */
    private Boolean ownerPreview;

    /** 是否开放评论（公域 status=1 时为 true） */
    private Boolean commentsEnabled;

    /** 视图模式：public / owner_preview */
    private String viewMode;

    /** 是否允许收藏 */
    private Boolean favoriteEnabled;

    /**
     * 标签列表，对应接口 Meme.memeTag
     */
    private List<MemeTagVO> memeTag;

    /**
     * 相关链接列表，对应接口 Meme.links
     */
    private List<MemeResourceVO> links;
}

