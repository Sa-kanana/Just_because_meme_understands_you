-- 中文注释：移除仅由旧构建产物执行、当前应用代码未使用的 user.public_uuid 字段，保留其历史迁移记录。
-- 中文注释：先检查并删除 UUID 唯一索引，确保字段删除不受索引依赖影响。
SET @has_user_public_uuid_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND index_name = 'uk_user_public_uuid'
);
SET @drop_user_public_uuid_index = IF(
    @has_user_public_uuid_index > 0,
    'DROP INDEX uk_user_public_uuid ON user',
    'SELECT 1'
);
PREPARE drop_user_public_uuid_index_stmt FROM @drop_user_public_uuid_index;
EXECUTE drop_user_public_uuid_index_stmt;
DEALLOCATE PREPARE drop_user_public_uuid_index_stmt;

-- 中文注释：仅当 UUID 列存在时删除该列及旧构建生成的值；当前应用没有读取该字段。
SET @has_user_public_uuid = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND column_name = 'public_uuid'
);
SET @drop_user_public_uuid = IF(
    @has_user_public_uuid > 0,
    'ALTER TABLE user DROP COLUMN public_uuid',
    'SELECT 1'
);
PREPARE drop_user_public_uuid_stmt FROM @drop_user_public_uuid;
EXECUTE drop_user_public_uuid_stmt;
DEALLOCATE PREPARE drop_user_public_uuid_stmt;
