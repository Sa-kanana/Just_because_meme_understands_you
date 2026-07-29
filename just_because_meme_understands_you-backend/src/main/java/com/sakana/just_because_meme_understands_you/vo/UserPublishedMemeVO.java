package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户发布的梗列表项，对齐「获取发布的梗」接口
 *
 * @author sakana
 */
@Data
public class UserPublishedMemeVO {

    /** 梗 id（Apifox SimpleMeme.id） */
    private Integer id;

    /** 梗 id（兼容旧前端字段） */
    private Integer memeId;

    /** 梗名称 */
    private String name;

    /** 梗介绍 */
    private String introduction;

    /** 封面图 URL */
    private String image;

    /** 浏览量 */
    private Integer pageViews;

    /** 点赞数 */
    private Integer likes;

    /** 评论数 */
    private Integer comments;

    /** 状态：1正常 2审核中 3主动下架 5锁定 6恢复审核中 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 下架/锁定原因 */
    private String offlineReason;

    /** 申诉驳回次数 */
    private Integer appealRejectCount;

    /** 关联标签 */
    private List<UserMemeTagVO> tags;

    /** 创建/发布时间（兼容旧字段） */
    private LocalDateTime createTime;

    /** 发布时间（对齐 SimpleMeme.releaseTime） */
    private LocalDateTime releaseTime;

    /** 发布者 */
    private AuthorVO author;
}
