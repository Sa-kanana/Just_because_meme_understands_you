-- 共享用户 RBAC：普通用户和管理人员共用 user/user_auth，仅通过角色关联授权。
-- 本迁移只把历史 ROLE_USER / ROLE_ADMIN 转成角色关联，不为任何账号自动授予 SUPER_ADMIN。

CREATE TABLE IF NOT EXISTS `role`
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    code        VARCHAR(32)  NOT NULL COMMENT '角色代码：USER、ADMIN、SUPER_ADMIN',
    name        VARCHAR(64)  NOT NULL COMMENT '角色中文名称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '角色创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) COMMENT='系统角色定义表';

INSERT INTO `role` (code, name)
VALUES ('USER', '普通用户'), ('ADMIN', '管理员'), ('SUPER_ADMIN', '超级管理员')
ON DUPLICATE KEY UPDATE name = VALUES(name);

CREATE TABLE IF NOT EXISTS user_role
(
    user_id     BIGINT   NOT NULL COMMENT '关联的用户主键',
    role_id     BIGINT   NOT NULL COMMENT '关联的角色主键',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '角色分配时间',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id, user_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES `role` (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) COMMENT='用户角色关联表';

-- 逐项字段复制说明：来源字段 user.role 转换后写入目标字段 role.code；未知或空旧值按最低权限映射为 USER。
-- 逐项字段复制说明：来源字段 user.id 写入目标字段 user_role.user_id。
-- 逐项字段复制说明：来源字段 role.id 写入目标字段 user_role.role_id。
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT source_user.id,
       target_role.id
FROM `user` AS source_user
JOIN `role` AS target_role
  ON target_role.code = CASE UPPER(COALESCE(source_user.role, ''))
        WHEN 'ROLE_ADMIN' THEN 'ADMIN'
        WHEN 'ADMIN' THEN 'ADMIN'
        WHEN 'ROLE_SUPER_ADMIN' THEN 'SUPER_ADMIN'
        WHEN 'SUPER_ADMIN' THEN 'SUPER_ADMIN'
        WHEN 'ROLE_USER' THEN 'USER'
        WHEN 'USER' THEN 'USER'
        ELSE 'USER'
     END;

ALTER TABLE `user`
    DROP COLUMN role;
