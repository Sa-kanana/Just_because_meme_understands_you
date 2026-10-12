package com.sakana.just_because_meme_understands_you.modules.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 持久化记录管理员手动触发的梗向量双向校准进度。 */
@Data
@TableName("meme_vector_reconciliation_job")
public class MemeVectorReconciliationJob {

    /** 中文注释：校准任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中文注释：活动任务唯一占位；活动时为 1，失败或完成后清空。 */
    private Integer activeSlot;

    /** 中文注释：任务状态，取值为 PENDING、RUNNING、FAILED 或 COMPLETED。 */
    private String status;

    /** 中文注释：当前处理阶段，取值为 SYNC_PUBLISHED 或 CLEAN_ORPHAN。 */
    private String phase;

    /** 中文注释：Java 已发布梗扫描游标。 */
    private Long publishedCursor;

    /** 中文注释：MemeAgent 向量梗 ID 分页游标。 */
    private String vectorCursor;

    /** 中文注释：Java 已发布梗扫描数量。 */
    private Long publishedScanned;

    /** 中文注释：已写入 outbox 的 upsert 事件数量。 */
    private Long upsertQueued;

    /** 中文注释：向量库梗 ID 扫描数量。 */
    private Long vectorScanned;

    /** 中文注释：已写入 outbox 的 removal 事件数量。 */
    private Long removalQueued;

    /** 中文注释：发起任务的管理员用户 ID。 */
    private Long createdBy;

    /** 中文注释：持有当前租约的 worker 实例标识。 */
    private String leaseOwner;

    /** 中文注释：当前 worker 租约到期时间。 */
    private LocalDateTime leaseUntil;

    /** 中文注释：任务失败时的安全阶段标识。 */
    private String failureStage;

    /** 中文注释：任务创建时间。 */
    private LocalDateTime createdAt;

    /** 中文注释：任务最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 中文注释：任务首次开始处理时间。 */
    private LocalDateTime startedAt;

    /** 中文注释：任务失败或完成时间。 */
    private LocalDateTime finishedAt;
}
