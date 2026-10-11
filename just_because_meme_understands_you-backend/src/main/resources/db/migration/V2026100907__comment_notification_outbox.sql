-- 中文说明：为通知记录来源 Outbox 事件，保证重复投递时幂等；基础 schema 已包含目标结构时跳过重复 DDL。
-- 中文说明：仅当 user_notification 缺少来源事件字段时添加该字段，字段含义与基础 schema 保持一致。
SET @notification_has_source_event_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND COLUMN_NAME = 'source_event_id'
);
SET @notification_source_event_id_ddl = IF(
    @notification_has_source_event_id > 0,
    'SELECT 1',
    'ALTER TABLE user_notification ADD COLUMN source_event_id BIGINT UNSIGNED NULL COMMENT ''来源 Outbox 事件 ID；为空表示非 Outbox 通知'' AFTER group_key'
);
PREPARE notification_source_event_id_stmt FROM @notification_source_event_id_ddl;
EXECUTE notification_source_event_id_stmt;
DEALLOCATE PREPARE notification_source_event_id_stmt;

-- 中文说明：仅当 user_notification 缺少来源事件唯一索引时创建该索引。
SET @notification_has_source_event_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND INDEX_NAME = 'uk_user_notification_source_event'
);
SET @notification_source_event_index_ddl = IF(
    @notification_has_source_event_index > 0,
    'SELECT 1',
    'ALTER TABLE user_notification ADD UNIQUE KEY uk_user_notification_source_event (source_event_id)'
);
PREPARE notification_source_event_index_stmt FROM @notification_source_event_index_ddl;
EXECUTE notification_source_event_index_stmt;
DEALLOCATE PREPARE notification_source_event_index_stmt;
