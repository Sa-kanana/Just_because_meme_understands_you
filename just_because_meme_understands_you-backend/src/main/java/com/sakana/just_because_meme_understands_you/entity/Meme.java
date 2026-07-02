package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author sakana
 * @since 2026-03-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("meme")
public class Meme implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id唯一标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 介绍
     */
    @TableField("introduction")
    private String introduction;

    /**
     * 名字
     */
    @TableField("name")
    private String name;

    /**
     * 图片url
     */
    @TableField("image")
    private String image;

    /**
     * 浏览量
     */
    @TableField("page_views")
    private Integer pageViews;

    /**
     * 点赞数
     */
    @TableField("likes")
    private Integer likes;

    /**
     * 评论数
     */
    @TableField("comments")
    private Integer comments;

    /**
     * 状态（1.正常，2.审核中，3.下架）
     */
    @TableField("status")
    private Integer status;

    /**
     * 发布时间
     */
    @TableField("release_time")
    private LocalDateTime releaseTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 发布者用户ID
     */
    @TableField("user_id")
    private Long userId;

}
