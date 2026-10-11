-- 中文说明：为用户账号增加可恢复的逻辑删除时间，删除时保留用户、登录凭据、角色及其内容关联记录。
-- 中文说明：仅当 user 表缺少逻辑删除时间字段时添加；NULL 表示账号未删除。
SET @user_has_deleted_at = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'deleted_at'
);
SET @user_deleted_at_ddl = IF(
    @user_has_deleted_at > 0,
    'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN deleted_at DATETIME NULL COMMENT ''逻辑删除时间，NULL表示未删除'' AFTER update_time'
);
PREPARE user_deleted_at_stmt FROM @user_deleted_at_ddl;
EXECUTE user_deleted_at_stmt;
DEALLOCATE PREPARE user_deleted_at_stmt;

-- 中文说明：仅当 user 表缺少逻辑删除与创建时间联合索引时创建该索引。
SET @user_has_deleted_create_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND INDEX_NAME = 'idx_user_deleted_create_time'
);
SET @user_deleted_create_index_ddl = IF(
    @user_has_deleted_create_index > 0,
    'SELECT 1',
    'ALTER TABLE `user` ADD KEY idx_user_deleted_create_time (deleted_at, create_time, id)'
);
PREPARE user_deleted_create_index_stmt FROM @user_deleted_create_index_ddl;
EXECUTE user_deleted_create_index_stmt;
DEALLOCATE PREPARE user_deleted_create_index_stmt;
