-- MySQL 8 增量脚本；在 mysql.sql 初始化后执行。
CREATE TABLE IF NOT EXISTS meme_knowledge_card (
    meme_id BIGINT NOT NULL PRIMARY KEY COMMENT '梗ID',
    source_url VARCHAR(500) NULL COMMENT '资料来源链接',
    first_seen_date DATE NULL COMMENT '首次出现日期',
    popularity_context VARCHAR(2000) NULL COMMENT '流行背景',
    usage_examples VARCHAR(2000) NULL COMMENT '使用示例',
    evolution VARCHAR(2000) NULL COMMENT '传播演变过程',
    related_meme_ids VARCHAR(1000) NOT NULL DEFAULT '[]' COMMENT '相关梗ID JSON数组',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资料更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='梗资料卡';

CREATE TABLE IF NOT EXISTS meme_knowledge_correction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '纠错申请ID',
    meme_id BIGINT NOT NULL COMMENT '被纠错梗ID',
    user_id BIGINT NOT NULL COMMENT '提交人用户ID',
    changes_json TEXT NOT NULL COMMENT '修改内容JSON',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：待处理或已处理',
    handled_by BIGINT NULL COMMENT '处理管理员用户ID',
    handling_note VARCHAR(500) NULL COMMENT '管理员处理备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请创建时间',
    handled_at DATETIME NULL COMMENT '申请处理完成时间',
    KEY idx_knowledge_correction_status (status, created_at, id),
    KEY idx_knowledge_correction_meme (meme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='梗资料纠错申请';
