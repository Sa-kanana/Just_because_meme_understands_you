package com.sakana.just_because_meme_understands_you.modules.ai.domain.entity;

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

    /** 规范化正文的 SHA-256 去重指纹。 */
    @TableField("content_sha256")
    private String contentSha256;

    /** 向量索引状态：PENDING、PROCESSING、READY、FAILED、DELETE_PENDING、DELETED。 */
    @TableField("index_status")
    private String indexStatus;

    /** 单调递增的向量操作版本，用于拒绝过期任务。 */
    @TableField("index_revision")
    private Long indexRevision;

    /** 最近一次向量操作对应的事务发件箱事件 ID。 */
    @TableField("index_task_id")
    private Long indexTaskId;

    /** 最近一次失败摘要；重试期间展示当前错误，成功后清空。 */
    @TableField("index_error")
    private String indexError;

    /** 1 发布，0 删除 */
    private Integer status;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
