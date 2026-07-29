package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员知识库（MySQL SSOT），向量侧由 MemeAgent 异步灌库。
 */
@Data
@TableName("admin_knowledge")
public class AdminKnowledge {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String content;

    private String category;

    /** 逗号分隔标签 */
    private String tags;

    @TableField("content_hash")
    private String contentHash;

    /** 1 发布，0 删除 */
    private Integer status;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
