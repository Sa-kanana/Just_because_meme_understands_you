package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端梗向量校准任务进度；queued 数量表示写入 outbox，不代表向量已落库。 */
@Data
public class AiVectorReconciliationJobVO {

    /** 中文注释：校准任务 ID。 */
    private Long jobId;

    /** 中文注释：任务状态。 */
    private String status;

    /** 中文注释：当前校准阶段。 */
    private String phase;

    /** 中文注释：Java 已发布梗扫描数量。 */
    private Long publishedScanned;

    /** 中文注释：已写入 outbox 的梗索引更新事件数量。 */
    private Long upsertQueued;

    /** 中文注释：向量库梗 ID 扫描数量。 */
    private Long vectorScanned;

    /** 中文注释：已写入 outbox 的无效向量删除事件数量。 */
    private Long removalQueued;

    /** 中文注释：安全失败阶段标识。 */
    private String failureStage;

    /** 中文注释：任务创建时间。 */
    private LocalDateTime createdAt;

    /** 中文注释：任务最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 中文注释：任务失败或完成时间。 */
    private LocalDateTime finishedAt;
}
