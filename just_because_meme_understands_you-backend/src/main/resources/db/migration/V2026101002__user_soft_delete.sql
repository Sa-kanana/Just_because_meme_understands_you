-- 为用户账号增加可恢复的逻辑删除时间；删除时保留用户、登录凭据、角色及其内容关联记录。
ALTER TABLE `user`
    ADD COLUMN deleted_at DATETIME NULL COMMENT '逻辑删除时间，NULL表示未删除' AFTER update_time,
    ADD KEY idx_user_deleted_create_time (deleted_at, create_time, id);
