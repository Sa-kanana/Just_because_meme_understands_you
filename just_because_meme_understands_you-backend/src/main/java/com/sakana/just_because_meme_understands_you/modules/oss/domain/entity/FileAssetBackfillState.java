package com.sakana.just_because_meme_understands_you.modules.oss.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中文注释：历史文件引用回填的游标、完成状态和执行租约。 */
@Data
@TableName("file_asset_backfill_state")
public class FileAssetBackfillState {

    /** 中文注释：本次历史资产回填任务的唯一名称。 */
    @TableField("backfill_key")
    private String backfillKey;

    /** 中文注释：当前扫描的数据源及业务字段名称。 */
    @TableField("source_name")
    private String sourceName;

    /** 中文注释：当前数据源最近完成回填的主键游标。 */
    @TableField("last_source_id")
    private Long lastSourceId;

    /** 中文注释：回填任务执行状态。 */
    @TableField("status")
    private String status;

    /** 中文注释：当前持有回填租约的应用实例标识。 */
    @TableField("lease_owner")
    private String leaseOwner;

    /** 中文注释：回填租约过期时间。 */
    @TableField("lease_until")
    private LocalDateTime leaseUntil;

    /** 中文注释：本次回填开始时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 中文注释：全部数据源回填完成时间。 */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /** 中文注释：最近一次回填失败的错误摘要。 */
    @TableField("last_error")
    private String lastError;

    /** 中文注释：回填状态最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
