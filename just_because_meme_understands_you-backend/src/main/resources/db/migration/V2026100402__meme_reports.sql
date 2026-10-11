-- MySQL 8 增量脚本；在现有 mysql.sql 初始化后执行，可重复执行。
CREATE TABLE IF NOT EXISTS meme_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '举报记录ID',
    meme_id BIGINT NOT NULL COMMENT '被举报梗ID',
    reporter_user_id BIGINT NOT NULL COMMENT '举报人用户ID',
    reason VARCHAR(32) NOT NULL COMMENT '举报原因编码',
    detail VARCHAR(500) NULL COMMENT '举报补充说明',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：待处理或已处理',
    result VARCHAR(16) NULL COMMENT '处理结果编码',
    handled_by BIGINT NULL COMMENT '处理管理员用户ID',
    handling_note VARCHAR(500) NULL COMMENT '管理员处理备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报创建时间',
    handled_at DATETIME NULL COMMENT '举报处理完成时间',
    UNIQUE KEY uq_meme_reporter (meme_id, reporter_user_id),
    KEY idx_report_status_created (status, created_at, id),
    KEY idx_report_meme (meme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='梗举报处理记录';
