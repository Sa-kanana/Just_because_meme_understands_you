-- 中文说明：仅软删除根评论缺失或已删除的活动回复；root_id 大于 0 表示该记录是回复。
-- 中文说明：来源字段 reply.root_id 与 reply.meme_id；目标字段 root.id 与 root.meme_id，用于确认根评论仍存在且属于同一梗。
-- 中文说明：reply.is_deleted 标记回复删除状态；reply.update_time 记录本次数据校准时间。
UPDATE meme_comment AS reply
LEFT JOIN meme_comment AS root
    ON root.id = reply.root_id
    AND root.meme_id = reply.meme_id
    AND root.is_deleted = 0
SET reply.is_deleted = 1,
    reply.update_time = CURRENT_TIMESTAMP
WHERE reply.is_deleted = 0
    AND reply.root_id > 0
    AND root.id IS NULL;

-- 中文说明：来源字段 source_comment.meme_id 将有效评论按梗聚合；source_comment.id 提供每条评论的计数。
-- 中文说明：source_comment.is_deleted = 0 表示仍可见的评论；目标字段 target_meme.id 关联梗，target_meme.comments 保存有效评论总数（含回复）。
UPDATE meme AS target_meme
LEFT JOIN (
    SELECT
        source_comment.meme_id AS meme_id,
        COUNT(source_comment.id) AS active_comment_count
    FROM meme_comment AS source_comment
    WHERE source_comment.is_deleted = 0
    GROUP BY source_comment.meme_id
) AS active_comment_summary
    ON active_comment_summary.meme_id = target_meme.id
SET target_meme.comments = COALESCE(active_comment_summary.active_comment_count, 0);

-- 中文说明：来源字段 source_reply.root_id 与 source_reply.id 统计活动回复；目标字段 target_root.id 关联根评论。
-- 中文说明：target_root.reply_count 保存当前活动回复数；target_root.root_id = 0 用于限定根评论。
UPDATE meme_comment AS target_root
LEFT JOIN (
    SELECT
        source_reply.root_id AS root_id,
        COUNT(source_reply.id) AS active_reply_count
    FROM meme_comment AS source_reply
    WHERE source_reply.is_deleted = 0
        AND source_reply.root_id > 0
    GROUP BY source_reply.root_id
) AS active_reply_summary
    ON active_reply_summary.root_id = target_root.id
SET target_root.reply_count = COALESCE(active_reply_summary.active_reply_count, 0)
WHERE target_root.root_id = 0;
