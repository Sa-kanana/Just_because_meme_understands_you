-- 目的：为邮箱 827278063@qq.com 对应的正常用户添加 SUPER_ADMIN 角色；不移除该用户已有角色。
-- 使用方式：在 DataGrip 中连接项目的 meme 数据库，打开本文件并执行全部语句。

-- 目标字段 @target_user_id 来源于 user_auth.user_id；通过 identity_type=email 与邮箱定位用户。
SET @target_user_id := (
    SELECT user_auth.user_id
    FROM user_auth
    JOIN user ON user.id = user_auth.user_id
    WHERE user_auth.identity_type = 'email'
      AND LOWER(user_auth.identifier) = LOWER('827278063@qq.com')
      AND user.status = 1
    LIMIT 1
);

-- 目标字段 @super_admin_role_id 来源于 role.id；筛选条件 role.code=SUPER_ADMIN。
SET @super_admin_role_id := (
    SELECT role.id
    FROM `role` AS role
    WHERE role.code = 'SUPER_ADMIN'
    LIMIT 1
);

START TRANSACTION;

-- 目标字段 user_role.user_id 来源于 @target_user_id，即上述邮箱对应的正常用户。
-- 目标字段 user_role.role_id 来源于 @super_admin_role_id，即 SUPER_ADMIN 角色主键。
-- user_role.create_time 使用表定义的 CURRENT_TIMESTAMP 默认值。
-- INSERT IGNORE 利用 user_role(user_id, role_id) 联合主键保证重复执行不会重复添加。
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT @target_user_id, @super_admin_role_id
WHERE @target_user_id IS NOT NULL
  AND @super_admin_role_id IS NOT NULL;

-- 执行结果：查询目标用户当前全部角色，确认 SUPER_ADMIN 已存在且原有角色保留。
-- 输出字段 user.id：目标用户主键；user.nickname：目标用户昵称。
-- 输出字段 user_auth.identifier：用于定位目标账号的邮箱；roles：该用户现有角色代码集合。
SELECT user.id,
       user.nickname,
       user_auth.identifier AS email,
       GROUP_CONCAT(DISTINCT role.code ORDER BY role.code SEPARATOR ',') AS roles
FROM user
JOIN user_auth ON user_auth.user_id = user.id
LEFT JOIN user_role ON user_role.user_id = user.id
LEFT JOIN `role` AS role ON role.id = user_role.role_id
WHERE user.id = @target_user_id
  AND user_auth.identity_type = 'email'
GROUP BY user.id, user.nickname, user_auth.identifier;

COMMIT;
