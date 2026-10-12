-- 中文注释：管理员手动发起的梗向量双向校准任务，可通过游标和租约在进程重启后恢复。
CREATE TABLE IF NOT EXISTS meme_vector_reconciliation_job (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '校准任务主键',
    `active_slot` TINYINT NULL COMMENT '活动任务唯一占位；活动任务为 1，终态为空',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING、RUNNING、FAILED 或 COMPLETED',
    `phase` VARCHAR(20) NOT NULL DEFAULT 'SYNC_PUBLISHED' COMMENT '当前阶段：同步已发布梗或清理无效向量',
    `published_cursor` BIGINT NOT NULL DEFAULT 0 COMMENT 'Java 已发布梗扫描到的最后一个梗 ID',
    `vector_cursor` VARCHAR(64) NULL COMMENT 'MemeAgent 向量 ID 分页的下一页游标',
    `published_scanned` BIGINT NOT NULL DEFAULT 0 COMMENT 'Java 已扫描的已发布梗数量',
    `upsert_queued` BIGINT NOT NULL DEFAULT 0 COMMENT '已写入 outbox 的梗索引更新事件数量',
    `vector_scanned` BIGINT NOT NULL DEFAULT 0 COMMENT '已扫描的向量库梗 ID 数量',
    `removal_queued` BIGINT NOT NULL DEFAULT 0 COMMENT '已写入 outbox 的无效向量删除事件数量',
    `created_by` BIGINT NOT NULL COMMENT '发起校准任务的管理员用户 ID',
    `lease_owner` VARCHAR(64) NULL COMMENT '当前处理任务的 worker 实例标识',
    `lease_until` DATETIME(3) NULL COMMENT '当前 worker 租约到期时间',
    `failure_stage` VARCHAR(64) NULL COMMENT '失败时保留的安全阶段标识',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '任务最后更新时间',
    `started_at` DATETIME(3) NULL COMMENT '任务首次开始处理时间',
    `finished_at` DATETIME(3) NULL COMMENT '任务失败或完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mvrj_active_slot` (`active_slot`),
    KEY `idx_mvrj_status_lease` (`status`, `lease_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员手动触发的梗向量库双向校准任务';
