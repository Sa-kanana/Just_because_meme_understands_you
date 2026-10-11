-- 中文说明：本迁移以截图中同时存在 meme_report 与 community_report 为前提。
-- 中文说明：应用发布前应暂停写入，完成迁移后再部署只使用 community_report 的代码。

-- 中文说明：逐项把旧梗举报字段写入统一举报表；不复制旧主键，避免与评论举报主键冲突。
INSERT INTO community_report (
    target_type, -- 目标字段：举报对象类型；来源字段：固定映射为梗举报 MEME
    target_id, -- 目标字段：被举报对象ID；来源字段：meme_report.meme_id
    meme_id, -- 目标字段：所属梗ID；来源字段：meme_report.meme_id
    comment_id, -- 目标字段：评论ID；来源字段：旧梗举报没有评论，固定写入 NULL
    reporter_user_id, -- 目标字段：举报人ID；来源字段：meme_report.reporter_user_id
    reason, -- 目标字段：举报原因；来源字段：meme_report.reason
    detail, -- 目标字段：补充说明；来源字段：meme_report.detail
    status, -- 目标字段：处理状态；来源字段：meme_report.status
    result, -- 目标字段：处理结果；来源字段：meme_report.result
    handled_by, -- 目标字段：处理管理员ID；来源字段：meme_report.handled_by
    handling_note, -- 目标字段：处理备注；来源字段：meme_report.handling_note
    created_at, -- 目标字段：举报时间；来源字段：meme_report.created_at
    handled_at -- 目标字段：处理时间；来源字段：meme_report.handled_at
)
SELECT
    'MEME', -- 来源字段：固定常量；目标字段：community_report.target_type
    legacy.meme_id, -- 来源字段：meme_report.meme_id；目标字段：community_report.target_id
    legacy.meme_id, -- 来源字段：meme_report.meme_id；目标字段：community_report.meme_id
    NULL, -- 来源字段：旧梗举报无评论ID；目标字段：community_report.comment_id
    legacy.reporter_user_id, -- 来源字段：meme_report.reporter_user_id；目标字段：community_report.reporter_user_id
    legacy.reason, -- 来源字段：meme_report.reason；目标字段：community_report.reason
    legacy.detail, -- 来源字段：meme_report.detail；目标字段：community_report.detail
    legacy.status, -- 来源字段：meme_report.status；目标字段：community_report.status
    legacy.result, -- 来源字段：meme_report.result；目标字段：community_report.result
    legacy.handled_by, -- 来源字段：meme_report.handled_by；目标字段：community_report.handled_by
    legacy.handling_note, -- 来源字段：meme_report.handling_note；目标字段：community_report.handling_note
    legacy.created_at, -- 来源字段：meme_report.created_at；目标字段：community_report.created_at
    legacy.handled_at -- 来源字段：meme_report.handled_at；目标字段：community_report.handled_at
FROM meme_report AS legacy
WHERE NOT EXISTS (
    SELECT 1
    FROM community_report AS current_report
    WHERE current_report.target_type = 'MEME'
      AND current_report.target_id = legacy.meme_id
      AND current_report.reporter_user_id = legacy.reporter_user_id
);

-- 中文说明：归档表保留旧主键和索引；mysql.sql 已预建归档表时该语句不会覆盖它。
CREATE TABLE IF NOT EXISTS meme_report_archive LIKE meme_report;
ALTER TABLE meme_report_archive COMMENT = '历史梗举报记录归档，仅用于审计查询';

-- 中文说明：逐项将原始举报字段复制到归档表，保留旧主键供审计追溯。
INSERT IGNORE INTO meme_report_archive (
    id, -- 目标字段：归档记录主键；来源字段：meme_report.id
    meme_id, -- 目标字段：被举报梗ID；来源字段：meme_report.meme_id
    reporter_user_id, -- 目标字段：举报人ID；来源字段：meme_report.reporter_user_id
    reason, -- 目标字段：举报原因；来源字段：meme_report.reason
    detail, -- 目标字段：补充说明；来源字段：meme_report.detail
    status, -- 目标字段：处理状态；来源字段：meme_report.status
    result, -- 目标字段：处理结果；来源字段：meme_report.result
    handled_by, -- 目标字段：处理管理员ID；来源字段：meme_report.handled_by
    handling_note, -- 目标字段：处理备注；来源字段：meme_report.handling_note
    created_at, -- 目标字段：举报时间；来源字段：meme_report.created_at
    handled_at -- 目标字段：处理时间；来源字段：meme_report.handled_at
)
SELECT
    legacy.id, -- 来源字段：meme_report.id；目标字段：meme_report_archive.id
    legacy.meme_id, -- 来源字段：meme_report.meme_id；目标字段：meme_report_archive.meme_id
    legacy.reporter_user_id, -- 来源字段：meme_report.reporter_user_id；目标字段：meme_report_archive.reporter_user_id
    legacy.reason, -- 来源字段：meme_report.reason；目标字段：meme_report_archive.reason
    legacy.detail, -- 来源字段：meme_report.detail；目标字段：meme_report_archive.detail
    legacy.status, -- 来源字段：meme_report.status；目标字段：meme_report_archive.status
    legacy.result, -- 来源字段：meme_report.result；目标字段：meme_report_archive.result
    legacy.handled_by, -- 来源字段：meme_report.handled_by；目标字段：meme_report_archive.handled_by
    legacy.handling_note, -- 来源字段：meme_report.handling_note；目标字段：meme_report_archive.handling_note
    legacy.created_at, -- 来源字段：meme_report.created_at；目标字段：meme_report_archive.created_at
    legacy.handled_at -- 来源字段：meme_report.handled_at；目标字段：meme_report_archive.handled_at
FROM meme_report AS legacy;

-- 中文说明：迁移完成后只保留 community_report 和 meme_report_archive 两个明确用途的表。
DROP TABLE meme_report;

-- 中文说明：移除不存在梗或标签的无效关联行；只删除无效关系，不删除梗或标签数据。
DELETE relation_row
FROM meme_tag_relation AS relation_row
LEFT JOIN meme AS meme_row ON meme_row.id = relation_row.meme_id
WHERE meme_row.id IS NULL;

DELETE relation_row
FROM meme_tag_relation AS relation_row
LEFT JOIN meme_tag AS tag_row ON tag_row.id = relation_row.meme_tag_id
WHERE tag_row.id IS NULL;

-- 中文说明：修正关联字段类型，并由数据库保证梗和标签两端记录存在。
ALTER TABLE meme_tag_relation
    MODIFY COLUMN meme_id BIGINT NOT NULL COMMENT '梗ID',
    MODIFY COLUMN meme_tag_id INT NOT NULL COMMENT '标签ID';

-- 中文说明：根据系统目录判断外键是否已由初始化结构创建，避免重复添加。
SET @add_meme_fk_sql = IF(
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'meme_tag_relation'
       AND CONSTRAINT_NAME = 'fk_meme_tag_relation_meme') = 0,
    'ALTER TABLE meme_tag_relation ADD CONSTRAINT fk_meme_tag_relation_meme FOREIGN KEY (meme_id) REFERENCES meme (id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE add_meme_fk_stmt FROM @add_meme_fk_sql;
EXECUTE add_meme_fk_stmt;
DEALLOCATE PREPARE add_meme_fk_stmt;

SET @add_tag_fk_sql = IF(
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'meme_tag_relation'
       AND CONSTRAINT_NAME = 'fk_meme_tag_relation_tag') = 0,
    'ALTER TABLE meme_tag_relation ADD CONSTRAINT fk_meme_tag_relation_tag FOREIGN KEY (meme_tag_id) REFERENCES meme_tag (id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE add_tag_fk_stmt FROM @add_tag_fk_sql;
EXECUTE add_tag_fk_stmt;
DEALLOCATE PREPARE add_tag_fk_stmt;

-- 中文说明：服务会复用已删除行；若历史上有效行与删除行并存，保留有效行。
DELETE deleted_like
FROM user_like AS deleted_like
JOIN user_like AS active_like
  ON active_like.user_id = deleted_like.user_id
 AND active_like.meme_id = deleted_like.meme_id
 AND active_like.is_deleted = 0
WHERE deleted_like.is_deleted = 1;

DELETE deleted_favorite
FROM user_favorite AS deleted_favorite
JOIN user_favorite AS active_favorite
  ON active_favorite.user_id = deleted_favorite.user_id
 AND active_favorite.meme_id = deleted_favorite.meme_id
 AND active_favorite.is_deleted = 0
WHERE deleted_favorite.is_deleted = 1;

-- 中文说明：唯一键与服务的“原记录恢复/软删除”状态机一致。
SET @drop_like_state_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_like'
       AND INDEX_NAME = 'uk_user_meme_deleted') > 0,
    'ALTER TABLE user_like DROP INDEX uk_user_meme_deleted',
    'SELECT 1'
);
PREPARE drop_like_state_index_stmt FROM @drop_like_state_index_sql;
EXECUTE drop_like_state_index_stmt;
DEALLOCATE PREPARE drop_like_state_index_stmt;

SET @add_like_unique_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_like'
       AND INDEX_NAME = 'uk_user_meme') = 0,
    'ALTER TABLE user_like ADD UNIQUE KEY uk_user_meme (user_id, meme_id)',
    'SELECT 1'
);
PREPARE add_like_unique_stmt FROM @add_like_unique_sql;
EXECUTE add_like_unique_stmt;
DEALLOCATE PREPARE add_like_unique_stmt;

SET @drop_favorite_state_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_favorite'
       AND INDEX_NAME = 'uk_user_meme_deleted') > 0,
    'ALTER TABLE user_favorite DROP INDEX uk_user_meme_deleted',
    'SELECT 1'
);
PREPARE drop_favorite_state_index_stmt FROM @drop_favorite_state_index_sql;
EXECUTE drop_favorite_state_index_stmt;
DEALLOCATE PREPARE drop_favorite_state_index_stmt;

SET @add_favorite_unique_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_favorite'
       AND INDEX_NAME = 'uk_user_meme') = 0,
    'ALTER TABLE user_favorite ADD UNIQUE KEY uk_user_meme (user_id, meme_id)',
    'SELECT 1'
);
PREPARE add_favorite_unique_stmt FROM @add_favorite_unique_sql;
EXECUTE add_favorite_unique_stmt;
DEALLOCATE PREPARE add_favorite_unique_stmt;

-- 中文说明：历史 NULL 计数按 0 处理，之后由 NOT NULL 约束阻止再次写入 NULL。
UPDATE meme
SET page_views = COALESCE(page_views, 0), -- 中文说明：NULL 浏览量归零
    likes = COALESCE(likes, 0), -- 中文说明：NULL 点赞数归零
    comments = COALESCE(comments, 0); -- 中文说明：NULL 评论数归零

ALTER TABLE meme
    MODIFY COLUMN page_views INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    MODIFY COLUMN likes INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    MODIFY COLUMN comments INT NOT NULL DEFAULT 0 COMMENT '评论数';

UPDATE user_stats
SET meme_count = COALESCE(meme_count, 0), -- 中文说明：NULL 发布梗数量归零
    like_received = COALESCE(like_received, 0), -- 中文说明：NULL 获赞总数归零
    favorite_count = COALESCE(favorite_count, 0), -- 中文说明：NULL 收藏总数归零
    follow_count = COALESCE(follow_count, 0), -- 中文说明：NULL 关注总数归零
    fans_count = COALESCE(fans_count, 0); -- 中文说明：NULL 粉丝总数归零

ALTER TABLE user_stats
    MODIFY COLUMN meme_count INT NOT NULL DEFAULT 0 COMMENT '发布的梗数量',
    MODIFY COLUMN like_received INT NOT NULL DEFAULT 0 COMMENT '获得的获赞总数',
    MODIFY COLUMN favorite_count INT NOT NULL DEFAULT 0 COMMENT '收藏的梗数量',
    MODIFY COLUMN follow_count INT NOT NULL DEFAULT 0 COMMENT '关注人数',
    MODIFY COLUMN fans_count INT NOT NULL DEFAULT 0 COMMENT '粉丝人数';

-- 中文说明：删除与 uk_from_to 完全重复的唯一索引，不改变关系唯一性。
SET @drop_duplicate_relation_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_relation'
       AND INDEX_NAME = 'uk_relation') > 0,
    'ALTER TABLE user_relation DROP INDEX uk_relation',
    'SELECT 1'
);
PREPARE drop_duplicate_relation_index_stmt FROM @drop_duplicate_relation_index_sql;
EXECUTE drop_duplicate_relation_index_stmt;
DEALLOCATE PREPARE drop_duplicate_relation_index_stmt;
