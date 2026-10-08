-- 增量迁移：用户关注梗标签。重复执行不会删除现有数据。
CREATE TABLE IF NOT EXISTS user_tag_follow (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tag_id INT NOT NULL COMMENT '关注的梗标签ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (user_id, tag_id),
    KEY idx_tag_follow_tag (tag_id, user_id),
    CONSTRAINT fk_tag_follow_tag FOREIGN KEY (tag_id) REFERENCES meme_tag (id) ON DELETE CASCADE
) COMMENT='用户关注梗标签';
