-- 管理员知识向量索引状态由 MySQL 文档记录与事务发件箱共同维护。
-- MySQL 9.6 已移除 MD5()；本迁移使用 SHA2(..., 256) 生成正文指纹。
-- 每个结构步骤都先检查 information_schema，便于从 MySQL 非事务 DDL 的部分执行状态恢复。

-- 兼容失败旧版迁移已创建 content_md5 的情况：重命名并扩展为 64 位 SHA-256 指纹。
SET @knowledge_has_sha256 = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND COLUMN_NAME = 'content_sha256'
);
SET @knowledge_has_old_md5 = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND COLUMN_NAME = 'content_md5'
);
SET @knowledge_ddl = IF(@knowledge_has_sha256 > 0, 'SELECT 1',
    IF(@knowledge_has_old_md5 > 0,
       'ALTER TABLE admin_knowledge CHANGE COLUMN content_md5 content_sha256 CHAR(64) NULL COMMENT ''标准化正文 SHA-256 去重指纹'' AFTER content_hash',
       'ALTER TABLE admin_knowledge ADD COLUMN content_sha256 CHAR(64) NULL COMMENT ''标准化正文 SHA-256 去重指纹'' AFTER content_hash'));
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

-- 以下目标字段若在旧版迁移失败前已经创建，则保留并跳过；缺失字段逐一补齐。
SET @knowledge_has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND COLUMN_NAME = 'index_status'
);
SET @knowledge_ddl = IF(@knowledge_has_column > 0, 'SELECT 1',
    'ALTER TABLE admin_knowledge ADD COLUMN index_status VARCHAR(24) NOT NULL DEFAULT ''PENDING'' COMMENT ''向量索引状态：PENDING、PROCESSING、READY、FAILED、DELETE_PENDING、DELETED'' AFTER content_sha256');
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

SET @knowledge_has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND COLUMN_NAME = 'index_revision'
);
SET @knowledge_ddl = IF(@knowledge_has_column > 0, 'SELECT 1',
    'ALTER TABLE admin_knowledge ADD COLUMN index_revision BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''单调递增的向量操作版本'' AFTER index_status');
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

SET @knowledge_has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND COLUMN_NAME = 'index_task_id'
);
SET @knowledge_ddl = IF(@knowledge_has_column > 0, 'SELECT 1',
    'ALTER TABLE admin_knowledge ADD COLUMN index_task_id BIGINT UNSIGNED NULL COMMENT ''最近一次向量操作对应的发件箱事件 ID'' AFTER index_revision');
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

SET @knowledge_has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND COLUMN_NAME = 'index_error'
);
SET @knowledge_ddl = IF(@knowledge_has_column > 0, 'SELECT 1',
    'ALTER TABLE admin_knowledge ADD COLUMN index_error VARCHAR(1000) NULL COMMENT ''最近一次向量操作错误摘要'' AFTER index_task_id');
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

-- 逐项字段复制说明：来源字段 admin_knowledge.content 经移除 BOM、统一换行并折叠连续空白后计算 SHA-256，写入目标字段 admin_knowledge.content_sha256。
UPDATE admin_knowledge
SET content_sha256 = SHA2(TRIM(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(
        content,
        CONVERT(0xEFBBBF USING utf8mb4), ''),
        CONCAT(CHAR(13), CHAR(10)), CHAR(10)),
        CHAR(13), CHAR(10)),
        '[[:space:]]+', ' ')), 256);

-- 相同正文的旧记录保留一个唯一指纹；优先保留已发布文档，其次保留较小 ID，其他记录仍保留原文但指纹置空。
UPDATE admin_knowledge AS duplicate_row
JOIN admin_knowledge AS preferred_row
  ON preferred_row.content_sha256 = duplicate_row.content_sha256
 AND (
      (preferred_row.status = 1 AND duplicate_row.status = 0)
      OR (preferred_row.status = duplicate_row.status AND preferred_row.id < duplicate_row.id)
 )
SET duplicate_row.content_sha256 = NULL;

-- 若从较早版本部分执行恢复，删除旧名称索引后再建立新指纹唯一索引。
SET @knowledge_has_old_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND INDEX_NAME = 'uk_admin_knowledge_content_md5'
);
SET @knowledge_ddl = IF(@knowledge_has_old_index > 0,
    'ALTER TABLE admin_knowledge DROP INDEX uk_admin_knowledge_content_md5', 'SELECT 1');
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

SET @knowledge_has_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_knowledge' AND INDEX_NAME = 'uk_admin_knowledge_content_sha256'
);
SET @knowledge_ddl = IF(@knowledge_has_index > 0, 'SELECT 1',
    'ALTER TABLE admin_knowledge ADD UNIQUE KEY uk_admin_knowledge_content_sha256 (content_sha256)');
PREPARE knowledge_stmt FROM @knowledge_ddl;
EXECUTE knowledge_stmt;
DEALLOCATE PREPARE knowledge_stmt;

-- 逐项字段复制说明：来源字段 admin_knowledge.status 决定目标字段 admin_knowledge.index_status；已发布文档进入 PENDING，已删除文档进入 DELETE_PENDING。
-- 逐项字段复制说明：目标字段 admin_knowledge.index_revision 从初始版本 0 提升为首个向量任务版本 1。
UPDATE admin_knowledge
SET index_status = CASE WHEN status = 1 THEN 'PENDING' ELSE 'DELETE_PENDING' END,
    index_revision = 1,
    index_error = NULL;

-- 按文档 ID 与操作版本防重，避免从后续语句失败状态恢复时重复投递初始化任务。
INSERT INTO integration_outbox_event
    (aggregate_type, aggregate_id, event_type, payload, status, attempts, created_at, next_attempt_at)
SELECT 'ADMIN_KNOWLEDGE',
       knowledge.id,
       CASE WHEN knowledge.status = 1 THEN 'KNOWLEDGE_INDEX_UPSERT' ELSE 'KNOWLEDGE_INDEX_DELETE' END,
       JSON_OBJECT(
           'docId', CAST(knowledge.id AS CHAR),
           'revision', 1,
           'operation', CASE WHEN knowledge.status = 1 THEN 'UPSERT' ELSE 'DELETE' END
       ),
       'PENDING',
       0,
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3)
FROM admin_knowledge AS knowledge
WHERE NOT EXISTS (
    SELECT 1
    FROM integration_outbox_event AS existing_event
    WHERE existing_event.aggregate_type = 'ADMIN_KNOWLEDGE'
      AND existing_event.aggregate_id = knowledge.id
      AND existing_event.event_type = CASE
          WHEN knowledge.status = 1 THEN 'KNOWLEDGE_INDEX_UPSERT' ELSE 'KNOWLEDGE_INDEX_DELETE'
      END
      AND JSON_UNQUOTE(JSON_EXTRACT(existing_event.payload, '$.revision')) = '1'
);

-- 逐项字段复制说明：来源字段 integration_outbox_event.id 写入目标字段 admin_knowledge.index_task_id；来源字段 aggregate_id 匹配目标字段 admin_knowledge.id。
UPDATE admin_knowledge AS knowledge
JOIN integration_outbox_event AS event
  ON event.aggregate_type = 'ADMIN_KNOWLEDGE'
 AND event.aggregate_id = knowledge.id
 AND event.event_type IN ('KNOWLEDGE_INDEX_UPSERT', 'KNOWLEDGE_INDEX_DELETE')
SET knowledge.index_task_id = event.id
WHERE event.id = (
    SELECT MAX(latest.id)
    FROM integration_outbox_event AS latest
    WHERE latest.aggregate_type = 'ADMIN_KNOWLEDGE'
      AND latest.aggregate_id = knowledge.id
      AND latest.event_type IN ('KNOWLEDGE_INDEX_UPSERT', 'KNOWLEDGE_INDEX_DELETE')
      AND JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.revision')) = '1'
);
