-- MySQL 8 community safety migration. Run after feature_reports_20261004.sql.
-- Creates a unified moderation queue and user-owned mute/block relations.
-- Existing meme reports keep their original IDs and audit fields; the legacy
-- meme_report table is retained and this script is safe to run repeatedly.

CREATE TABLE IF NOT EXISTS community_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    target_type VARCHAR(16) NOT NULL COMMENT '举报对象类型：梗或评论',
    target_id BIGINT NOT NULL COMMENT '被举报对象ID',
    meme_id BIGINT NOT NULL COMMENT '举报对象所属梗ID',
    comment_id BIGINT NULL COMMENT '被举报评论ID，梗举报时为空',
    reporter_user_id BIGINT NOT NULL COMMENT '举报人用户ID',
    reason VARCHAR(32) NOT NULL COMMENT '举报原因编码',
    detail VARCHAR(500) NULL COMMENT '举报补充说明',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：待处理或已处理',
    result VARCHAR(24) NULL COMMENT '处理结果编码',
    handled_by BIGINT NULL COMMENT '处理管理员用户ID',
    handling_note VARCHAR(500) NULL COMMENT '管理员处理备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报创建时间',
    handled_at DATETIME NULL COMMENT '举报处理完成时间',
    UNIQUE KEY uq_community_report_target_reporter (target_type, target_id, reporter_user_id),
    KEY idx_community_report_status_created (status, created_at, id),
    KEY idx_community_report_meme (meme_id, created_at),
    KEY idx_community_report_comment (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一梗与评论举报队列';

CREATE TABLE IF NOT EXISTS user_suppression (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    owner_user_id BIGINT NOT NULL COMMENT '发起静音或屏蔽的用户ID',
    target_user_id BIGINT NOT NULL COMMENT '被静音或屏蔽的用户ID',
    type VARCHAR(16) NOT NULL COMMENT '关系类型：静音或屏蔽',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关系创建时间',
    UNIQUE KEY uq_user_suppression (owner_user_id, target_user_id, type),
    KEY idx_user_suppression_target (target_user_id, type),
    KEY idx_user_suppression_owner (owner_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户静音与屏蔽关系';

INSERT IGNORE INTO community_report (
    id, -- 迁移后沿用原举报记录ID
    target_type, -- 迁移后举报对象类型
    target_id, -- 迁移后被举报对象ID
    meme_id, -- 迁移后所属梗ID
    comment_id, -- 迁移后评论ID
    reporter_user_id, -- 迁移后举报人ID
    reason, -- 迁移后举报原因
    detail, -- 迁移后补充说明
    status, -- 迁移后处理状态
    result, -- 迁移后处理结果
    handled_by, -- 迁移后处理管理员ID
    handling_note, -- 迁移后处理备注
    created_at, -- 迁移后举报创建时间
    handled_at -- 迁移后处理时间
)
SELECT
    id, -- 来源：旧梗举报主键
    'MEME', -- 来源：固定映射为梗举报
    meme_id, -- 来源：旧表被举报梗ID
    meme_id, -- 来源：旧表梗ID作为所属梗ID
    NULL, -- 来源：旧表无评论对象
    reporter_user_id, -- 来源：旧表举报人ID
    reason, -- 来源：旧表举报原因
    detail, -- 来源：旧表补充说明
    status, -- 来源：旧表处理状态
    result, -- 来源：旧表处理结果
    handled_by, -- 来源：旧表处理管理员ID
    handling_note, -- 来源：旧表处理备注
    created_at, -- 来源：旧表举报创建时间
    handled_at -- 来源：旧表处理时间
FROM meme_report;
