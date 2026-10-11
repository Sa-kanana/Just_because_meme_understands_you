-- 中文说明：本脚本只读，用于执行 Flyway 数据结构迁移前定位孤儿关系和唯一键冲突。
-- 中文说明：每条查询返回需要人工核对的数据；请先备份，再按业务含义处理后重试 Flyway。

-- 中文说明：检查梗标签关系引用的梗是否存在。
SELECT
    relation_row.meme_id AS meme_id, -- 字段说明：meme_tag_relation.meme_id 中找不到对应 meme.id 的梗ID
    relation_row.meme_tag_id AS meme_tag_id -- 字段说明：该孤儿关系所关联的标签ID
FROM meme_tag_relation AS relation_row
LEFT JOIN meme AS meme_row ON meme_row.id = relation_row.meme_id
WHERE meme_row.id IS NULL;

-- 中文说明：检查梗标签关系引用的标签是否存在。
SELECT
    relation_row.meme_id AS meme_id, -- 字段说明：meme_tag_relation.meme_id 中对应的梗ID
    relation_row.meme_tag_id AS meme_tag_id -- 字段说明：meme_tag_relation.meme_tag_id 中找不到对应 meme_tag.id 的标签ID
FROM meme_tag_relation AS relation_row
LEFT JOIN meme_tag AS tag_row ON tag_row.id = relation_row.meme_tag_id
WHERE tag_row.id IS NULL;

-- 中文说明：检查点赞表中违反 (user_id, meme_id) 唯一键的记录组合。
SELECT
    user_like.user_id AS user_id, -- 字段说明：发生重复点赞关系的用户ID
    user_like.meme_id AS meme_id, -- 字段说明：发生重复点赞关系的梗ID
    COUNT(*) AS duplicate_row_count -- 字段说明：该用户与梗组合对应的明细行数，超过1表示冲突
FROM user_like
GROUP BY user_like.user_id, user_like.meme_id
HAVING COUNT(*) > 1;

-- 中文说明：检查收藏表中违反 (user_id, meme_id) 唯一键的记录组合。
SELECT
    user_favorite.user_id AS user_id, -- 字段说明：发生重复收藏关系的用户ID
    user_favorite.meme_id AS meme_id, -- 字段说明：发生重复收藏关系的梗ID
    COUNT(*) AS duplicate_row_count -- 字段说明：该用户与梗组合对应的明细行数，超过1表示冲突
FROM user_favorite
GROUP BY user_favorite.user_id, user_favorite.meme_id
HAVING COUNT(*) > 1;

-- 中文说明：检查梗资源引用的梗是否存在。
SELECT
    resource_row.id AS resource_id, -- 字段说明：meme_resource.id 中需要核对的资源主键
    resource_row.meme_id AS meme_id -- 字段说明：meme_resource.meme_id 中找不到对应 meme.id 的梗ID
FROM meme_resource AS resource_row
LEFT JOIN meme AS meme_row ON meme_row.id = resource_row.meme_id
WHERE meme_row.id IS NULL;

-- 中文说明：检查每日统计快照引用的梗是否存在。
SELECT
    snapshot_row.id AS snapshot_id, -- 字段说明：meme_daily_snapshot.id 中需要核对的快照主键
    snapshot_row.meme_id AS meme_id -- 字段说明：meme_daily_snapshot.meme_id 中找不到对应 meme.id 的梗ID
FROM meme_daily_snapshot AS snapshot_row
LEFT JOIN meme AS meme_row ON meme_row.id = snapshot_row.meme_id
WHERE meme_row.id IS NULL;

-- 中文说明：检查评论图片引用的评论是否存在。
SELECT
    image_row.id AS image_id, -- 字段说明：meme_comment_images.id 中需要核对的图片主键
    image_row.meme_comment_id AS meme_comment_id -- 字段说明：找不到对应 meme_comment.id 的评论主键
FROM meme_comment_images AS image_row
LEFT JOIN meme_comment AS comment_row ON comment_row.id = image_row.meme_comment_id
WHERE comment_row.id IS NULL;

-- 中文说明：检查 AI 消息引用的会话是否存在。
SELECT
    message_row.id AS message_id, -- 字段说明：ai_chat_message.id 中需要核对的消息主键
    message_row.session_id AS session_id -- 字段说明：找不到对应 ai_chat_session.id 的会话主键
FROM ai_chat_message AS message_row
LEFT JOIN ai_chat_session AS session_row ON session_row.id = message_row.session_id
WHERE session_row.id IS NULL;

-- 中文说明：检查账号凭证引用的用户是否存在。
SELECT
    auth_row.id AS auth_id, -- 字段说明：user_auth.id 中需要核对的凭证主键
    auth_row.user_id AS user_id -- 字段说明：找不到对应 user.id 的用户ID
FROM user_auth AS auth_row
LEFT JOIN user AS user_row ON user_row.id = auth_row.user_id
WHERE user_row.id IS NULL;

-- 中文说明：检查用户统计行引用的用户是否存在。
SELECT
    stats_row.user_id AS user_id -- 字段说明：user_stats.user_id 中找不到对应 user.id 的用户ID
FROM user_stats AS stats_row
LEFT JOIN user AS user_row ON user_row.id = stats_row.user_id
WHERE user_row.id IS NULL;
