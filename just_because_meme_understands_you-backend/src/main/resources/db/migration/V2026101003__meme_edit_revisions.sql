-- MySQL 8 增量脚本；保存作者对正式梗内容的修改快照和审核结果。
CREATE TABLE IF NOT EXISTS meme_edit_revision (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '修改稿记录ID',
    meme_id BIGINT NOT NULL COMMENT '被修改的梗ID',
    editor_user_id BIGINT NOT NULL COMMENT '提交修改的作者用户ID',
    base_content_hash CHAR(64) NOT NULL COMMENT '提交时正式内容的SHA-256摘要',
    content_json JSON NOT NULL COMMENT '梗名称、介绍、封面、标签和资源的修改内容快照',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING、APPROVED、REJECTED或CANCELED',
    review_note VARCHAR(500) NULL COMMENT '审核驳回或取消说明',
    reviewed_by BIGINT NULL COMMENT '处理该修改稿的管理员用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改稿提交时间',
    reviewed_at DATETIME NULL COMMENT '修改稿审核完成时间',
    PRIMARY KEY (id),
    KEY idx_meme_edit_revision_meme_status (meme_id, status),
    KEY idx_meme_edit_revision_status_created (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='梗内容修改审核记录';
