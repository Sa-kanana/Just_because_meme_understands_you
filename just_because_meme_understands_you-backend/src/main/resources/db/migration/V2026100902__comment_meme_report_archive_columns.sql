-- 中文说明：为已有举报归档表补齐表级和列级中文注释，不复制或修改业务数据。
ALTER TABLE meme_report_archive
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报记录主键',
    MODIFY COLUMN meme_id BIGINT NOT NULL COMMENT '被举报梗ID',
    MODIFY COLUMN reporter_user_id BIGINT NOT NULL COMMENT '举报人用户ID',
    MODIFY COLUMN reason VARCHAR(32) NOT NULL COMMENT '举报原因编码',
    MODIFY COLUMN detail VARCHAR(500) NULL COMMENT '举报补充说明',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：待处理或已处理',
    MODIFY COLUMN result VARCHAR(16) NULL COMMENT '处理结果编码',
    MODIFY COLUMN handled_by BIGINT NULL COMMENT '处理管理员用户ID',
    MODIFY COLUMN handling_note VARCHAR(500) NULL COMMENT '管理员处理备注',
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报创建时间',
    MODIFY COLUMN handled_at DATETIME NULL COMMENT '举报处理完成时间',
    COMMENT = '历史梗举报记录归档，仅用于审计查询';
