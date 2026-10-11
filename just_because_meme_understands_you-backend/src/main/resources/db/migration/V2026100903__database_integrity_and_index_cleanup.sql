-- 中文说明：本迁移清理重复索引、补充同模块外键并校准已有发布梗统计。
-- 中文说明：user_stats 中无法关联用户的统计记录先完整归档，再从在线表移除；归档数据保留供审计。

-- 中文说明：为失去用户主体的统计记录创建单独归档表，避免在线表留下无法关联的统计数据。
CREATE TABLE IF NOT EXISTS user_stats_orphan_archive
(
    archive_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '归档记录主键',
    source_user_id   BIGINT       NOT NULL COMMENT '来源 user_stats.user_id，原用户ID',
    meme_count       INT          NOT NULL COMMENT '来源 user_stats.meme_count，发布梗数量',
    like_received    INT          NOT NULL COMMENT '来源 user_stats.like_received，获得点赞数量',
    favorite_count   INT          NOT NULL COMMENT '来源 user_stats.favorite_count，收藏数量',
    follow_count     INT          NOT NULL COMMENT '来源 user_stats.follow_count，关注数量',
    fans_count       INT          NOT NULL COMMENT '来源 user_stats.fans_count，粉丝数量',
    archived_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '统计记录归档时间',
    PRIMARY KEY (archive_id),
    UNIQUE KEY uk_user_stats_orphan_source_user (source_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '无法关联用户的历史统计记录归档表';

-- 中文说明：逐项复制孤儿统计；每个目标字段和来源字段均注明映射，INSERT IGNORE 保证失败后重试不产生重复归档。
INSERT IGNORE INTO user_stats_orphan_archive
(
    source_user_id, -- 中文说明：目标字段 user_stats_orphan_archive.source_user_id，来源 user_stats.user_id
    meme_count,     -- 中文说明：目标字段 user_stats_orphan_archive.meme_count，来源 user_stats.meme_count
    like_received,  -- 中文说明：目标字段 user_stats_orphan_archive.like_received，来源 user_stats.like_received
    favorite_count, -- 中文说明：目标字段 user_stats_orphan_archive.favorite_count，来源 user_stats.favorite_count
    follow_count,   -- 中文说明：目标字段 user_stats_orphan_archive.follow_count，来源 user_stats.follow_count
    fans_count      -- 中文说明：目标字段 user_stats_orphan_archive.fans_count，来源 user_stats.fans_count
)
SELECT
    orphan_stats.user_id,        -- 中文说明：来源字段 user_stats.user_id；目标字段 user_stats_orphan_archive.source_user_id
    orphan_stats.meme_count,     -- 中文说明：来源字段 user_stats.meme_count；目标字段 user_stats_orphan_archive.meme_count
    orphan_stats.like_received,  -- 中文说明：来源字段 user_stats.like_received；目标字段 user_stats_orphan_archive.like_received
    orphan_stats.favorite_count, -- 中文说明：来源字段 user_stats.favorite_count；目标字段 user_stats_orphan_archive.favorite_count
    orphan_stats.follow_count,   -- 中文说明：来源字段 user_stats.follow_count；目标字段 user_stats_orphan_archive.follow_count
    orphan_stats.fans_count      -- 中文说明：来源字段 user_stats.fans_count；目标字段 user_stats_orphan_archive.fans_count
FROM user_stats AS orphan_stats
LEFT JOIN user AS parent_user ON parent_user.id = orphan_stats.user_id
WHERE parent_user.id IS NULL;

-- 中文说明：只有完成上方归档后才从在线统计表移除无主体行，原字段值完整保存在归档表。
DELETE orphan_stats
FROM user_stats AS orphan_stats
LEFT JOIN user AS parent_user ON parent_user.id = orphan_stats.user_id
WHERE parent_user.id IS NULL;

-- 中文说明：先验证所有候选外键都没有孤儿数据，避免前面部分 DDL 已提交后再因数据问题失败。
DROP PROCEDURE IF EXISTS codex_assert_database_references_clean;
DELIMITER $$
CREATE PROCEDURE codex_assert_database_references_clean()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM meme_resource AS child_row
        LEFT JOIN meme AS parent_row ON parent_row.id = child_row.meme_id
        WHERE parent_row.id IS NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'meme_resource 存在无对应 meme 的 meme_id，请先核查孤儿数据';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM meme_daily_snapshot AS child_row
        LEFT JOIN meme AS parent_row ON parent_row.id = child_row.meme_id
        WHERE parent_row.id IS NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'meme_daily_snapshot 存在无对应 meme 的 meme_id，请先核查孤儿数据';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM meme_comment_images AS child_row
        LEFT JOIN meme_comment AS parent_row ON parent_row.id = child_row.meme_comment_id
        WHERE parent_row.id IS NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'meme_comment_images 存在无对应评论的 meme_comment_id，请先核查孤儿数据';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM ai_chat_message AS child_row
        LEFT JOIN ai_chat_session AS parent_row ON parent_row.id = child_row.session_id
        WHERE parent_row.id IS NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_chat_message 存在无对应会话的 session_id，请先核查孤儿数据';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_auth AS child_row
        LEFT JOIN user AS parent_row ON parent_row.id = child_row.user_id
        WHERE parent_row.id IS NULL
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_auth 存在无对应用户的 user_id，请先核查孤儿数据';
    END IF;

END$$
DELIMITER ;

CALL codex_assert_database_references_clean();
DROP PROCEDURE codex_assert_database_references_clean;

-- 中文说明：以下过程仅在目标索引存在时删除，重复执行时跳过已删除索引。
DROP PROCEDURE IF EXISTS codex_drop_index_if_present;
DELIMITER $$
CREATE PROCEDURE codex_drop_index_if_present(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @codex_drop_index_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` DROP INDEX `', REPLACE(p_index_name, '`', '``'), '`'
        );
        PREPARE codex_drop_index_stmt FROM @codex_drop_index_sql;
        EXECUTE codex_drop_index_stmt;
        DEALLOCATE PREPARE codex_drop_index_stmt;
    END IF;
END$$
DELIMITER ;

-- 中文说明：复合索引可覆盖这些索引的左前缀；相同访问路径只保留一个索引。
CALL codex_drop_index_if_present('meme', 'idx_status_views');
CALL codex_drop_index_if_present('meme', 'idx_status_release_time');
CALL codex_drop_index_if_present('meme', 'idx_meme_user_id');
CALL codex_drop_index_if_present('user_relation', 'idx_from_user');
CALL codex_drop_index_if_present('user_relation', 'idx_to_user');
CALL codex_drop_index_if_present('user_favorite', 'idx_user_folder');
CALL codex_drop_index_if_present('meme_report_archive', 'idx_report_meme');
DROP PROCEDURE codex_drop_index_if_present;

-- 中文说明：为标签名称精确查询建立普通索引，不限制同名标签，避免改变现有业务语义。
SET @codex_add_meme_tag_name_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'meme_tag'
       AND INDEX_NAME = 'idx_meme_tag_name') = 0,
    'CREATE INDEX idx_meme_tag_name ON meme_tag (name)',
    'SELECT 1'
);
PREPARE codex_add_meme_tag_name_index_stmt FROM @codex_add_meme_tag_name_index_sql;
EXECUTE codex_add_meme_tag_name_index_stmt;
DEALLOCATE PREPARE codex_add_meme_tag_name_index_stmt;

-- 中文说明：以下过程仅在外键不存在时添加，已存在时保持原约束。
DROP PROCEDURE IF EXISTS codex_add_foreign_key_if_missing;
DELIMITER $$
CREATE PROCEDURE codex_add_foreign_key_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_referenced_table_name VARCHAR(64),
    IN p_referenced_column_name VARCHAR(64)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND CONSTRAINT_NAME = p_constraint_name
    ) THEN
        SET @codex_add_foreign_key_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD CONSTRAINT `', REPLACE(p_constraint_name, '`', '``'),
            '` FOREIGN KEY (`', REPLACE(p_column_name, '`', '``'),
            '`) REFERENCES `', REPLACE(p_referenced_table_name, '`', '``'),
            '` (`', REPLACE(p_referenced_column_name, '`', '``'), '`)',
            ' ON DELETE RESTRICT ON UPDATE RESTRICT'
        );
        PREPARE codex_add_foreign_key_stmt FROM @codex_add_foreign_key_sql;
        EXECUTE codex_add_foreign_key_stmt;
        DEALLOCATE PREPARE codex_add_foreign_key_stmt;
    END IF;
END$$
DELIMITER ;

-- 中文说明：只约束各自模块内的强关系；跨模块和多态 ID 仍由应用层维护。user_stats 孤儿记录已在前文归档并清理。
CALL codex_add_foreign_key_if_missing('meme_resource', 'fk_meme_resource_meme', 'meme_id', 'meme', 'id');
CALL codex_add_foreign_key_if_missing('meme_daily_snapshot', 'fk_meme_daily_snapshot_meme', 'meme_id', 'meme', 'id');
CALL codex_add_foreign_key_if_missing('meme_comment_images', 'fk_meme_comment_images_comment', 'meme_comment_id', 'meme_comment', 'id');
CALL codex_add_foreign_key_if_missing('ai_chat_message', 'fk_ai_chat_message_session', 'session_id', 'ai_chat_session', 'id');
CALL codex_add_foreign_key_if_missing('user_auth', 'fk_user_auth_user', 'user_id', 'user', 'id');
CALL codex_add_foreign_key_if_missing('user_stats', 'fk_user_stats_user', 'user_id', 'user', 'id');
DROP PROCEDURE codex_add_foreign_key_if_missing;

-- 中文说明：逐项以 meme.status=1 的梗记录作为来源，对齐已有统计行，不创建缺失的 user_stats 行。
UPDATE user_stats AS target_stats
LEFT JOIN (
    SELECT source_meme.user_id, COUNT(*) AS published_meme_count -- 中文说明：来源字段 meme.user_id 与 status；目标字段 user_stats.user_id 与 meme_count
    FROM meme AS source_meme
    WHERE source_meme.status = 1 -- 中文说明：仅统计当前已发布的梗
    GROUP BY source_meme.user_id
) AS source_meme_counts
    ON source_meme_counts.user_id = target_stats.user_id -- 中文说明：来源字段 meme.user_id；目标字段 user_stats.user_id
SET target_stats.meme_count = COALESCE(source_meme_counts.published_meme_count, 0); -- 中文说明：来源字段聚合计数；目标字段 user_stats.meme_count
