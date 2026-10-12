package com.sakana.just_because_meme_understands_you.modules.oss.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：持久化记录 OSS 文件资产删除、租约、重试和审计状态。 */
@Data
@TableName("file_delete_task")
public class FileAssetDeleteTask {

    /** 中文注释：删除任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中文注释：待删除文件资产主键。 */
    @TableField("asset_id")
    private Long assetId;

    /** 中文注释：创建任务时记录的 OSS 对象键快照。 */
    @TableField("object_key")
    private String objectKey;

    /** 中文注释：触发文件删除的业务原因。 */
    @TableField("reason")
    private String reason;

    /** 中文注释：任务处理状态，包括等待、处理中、完成、取消和失败。 */
    @TableField("status")
    private String status;

    /** 中文注释：已安排的重试次数。 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 中文注释：任务下一次允许执行的时间。 */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 中文注释：当前领取任务的应用实例标识。 */
    @TableField(value = "lease_owner", updateStrategy = FieldStrategy.ALWAYS)
    private String leaseOwner;

    /** 中文注释：当前处理租约的过期时间。 */
    @TableField(value = "lease_until", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime leaseUntil;

    /** 中文注释：最近一次开始处理任务的时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 中文注释：任务完成、取消或进入终态的时间。 */
    @TableField(value = "finished_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;

    /** 中文注释：最近一次失败的错误摘要。 */
    @TableField(value = "last_error", updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;

    /** 中文注释：删除任务创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 中文注释：删除任务最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
