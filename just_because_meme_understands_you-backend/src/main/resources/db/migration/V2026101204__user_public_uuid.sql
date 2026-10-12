-- 中文注释：保留本地构建残留中已被数据库执行的 UUID 迁移历史，下一版本会审计清理该未使用字段。
-- 中文注释：仅在 public_uuid 不存在时添加；该字段曾用于公开主页 UUID 标识。
SET @has_user_public_uuid = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND column_name = 'public_uuid'
);
SET @add_user_public_uuid = IF(
    @has_user_public_uuid = 0,
    'ALTER TABLE user ADD COLUMN public_uuid CHAR(36) NULL COMMENT ''对外分享个人主页使用的稳定 UUID'' AFTER id',
    'SELECT 1'
);
PREPARE add_user_public_uuid_stmt FROM @add_user_public_uuid;
EXECUTE add_user_public_uuid_stmt;
DEALLOCATE PREPARE add_user_public_uuid_stmt;

-- 中文注释：来源为尚未分配标识的 user.id，目标为该用户新生成的小写 UUID。
UPDATE user
SET public_uuid = LOWER(UUID())
WHERE public_uuid IS NULL OR TRIM(public_uuid) = '';

-- 中文注释：回填后将 public_uuid 设为必填；字段用途由列注释记录。
ALTER TABLE user
    MODIFY COLUMN public_uuid CHAR(36) NOT NULL COMMENT '对外分享个人主页使用的稳定 UUID';

-- 中文注释：仅在公开 UUID 唯一索引不存在时创建，确保每个 UUID 只对应一个用户。
SET @has_user_public_uuid_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND index_name = 'uk_user_public_uuid'
);
SET @add_user_public_uuid_index = IF(
    @has_user_public_uuid_index = 0,
    'CREATE UNIQUE INDEX uk_user_public_uuid ON user (public_uuid)',
    'SELECT 1'
);
PREPARE add_user_public_uuid_index_stmt FROM @add_user_public_uuid_index;
EXECUTE add_user_public_uuid_index_stmt;
DEALLOCATE PREPARE add_user_public_uuid_index_stmt;
