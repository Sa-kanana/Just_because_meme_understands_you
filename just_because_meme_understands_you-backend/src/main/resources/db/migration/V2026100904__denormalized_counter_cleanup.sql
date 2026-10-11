-- 中文说明：按全部评论明细校准累计评论数；来源包含软删除评论，与评论创建时的累计口径一致。
UPDATE meme AS target_meme
LEFT JOIN (
    SELECT
        source_comment.meme_id AS meme_id, -- 中文说明：来源字段 meme_comment.meme_id；目标字段 meme.id
        COUNT(source_comment.id) AS actual_comment_count -- 中文说明：来源字段 meme_comment.id 聚合总数；目标字段 meme.comments
    FROM meme_comment AS source_comment
    GROUP BY source_comment.meme_id
) AS source_comment_counts
    ON source_comment_counts.meme_id = target_meme.id -- 中文说明：来源字段 meme_comment.meme_id；目标字段 meme.id
SET target_meme.comments = COALESCE(source_comment_counts.actual_comment_count, 0); -- 中文说明：来源字段 meme_comment.id 聚合总数；目标字段 meme.comments

-- 中文说明：user_stats.meme_count 未被发布、下架与恢复流程完整维护；主页发布数改由 meme.user_id/status 实时统计。
ALTER TABLE user_stats
    DROP COLUMN meme_count;
