package com.sakana.just_because_meme_understands_you.modules.oss.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：由业务事务创建并由后台 Worker 重试执行的 OSS 删除任务。 */
@Data
@TableName("file_delete_task")
public class FileDeleteTask {

    /** 中文注释：文件删除任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中文注释：待删除的文件资产ID。 */
    @TableField("asset_id")
    private Long assetId;

    /** 中文注释：创建任务时的 OSS 对象键快照。 */
    @TableField("object_key")
    private String objectKey;

    /** 中文注释：触发删除任务的业务原因。 */
    @TableField("reason")
    private String reason;

    /** 中文注释：任务处理状态。 */
    @TableField("status")
    private String status;

    /** 中文注释：已执行的删除尝试次数。 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 中文注释：下一次允许执行任务的时间。 */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 中文注释：当前领取任务的应用实例标识。 */
    @TableField("lease_owner")
    private String leaseOwner;

    /** 中文注释：当前任务租约过期时间。 */
    @TableField("lease_until")
    private LocalDateTime leaseUntil;

    /** 中文注释：最近一次开始处理的时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 中文注释：任务完成、取消或终止的时间。 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 中文注释：最近一次删除失败的错误摘要。 */
    @TableField("last_error")
    private String lastError;

    /** 中文注释：任务创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 中文注释：任务最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
