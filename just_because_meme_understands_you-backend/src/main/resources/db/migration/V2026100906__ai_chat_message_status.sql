-- 中文说明：为 AI 消息保存回答终态；历史消息和用户问题默认标记为已完成。
-- 中文说明：完整基线已含 status；旧库仅在缺少该列时补充，避免重复添加。
SET @add_ai_chat_message_status_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ai_chat_message'
       AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE ai_chat_message ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''COMPLETED'' COMMENT ''消息状态：COMPLETED已完成、STOPPED用户停止、FAILED生成失败''',
    'SELECT 1'
);
PREPARE add_ai_chat_message_status_stmt FROM @add_ai_chat_message_status_sql;
EXECUTE add_ai_chat_message_status_stmt;
DEALLOCATE PREPARE add_ai_chat_message_status_stmt;
