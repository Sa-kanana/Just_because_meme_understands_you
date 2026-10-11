-- 中文说明：本文件只读预览迁移影响范围并核对冗余计数，不会修改或修复任何数据。
-- 中文说明：迁移前可先检查 Flyway 版本及迁移将处理的历史行，再查看计数差异。

-- 中文说明：检查目标迁移是否已经成功执行；无结果表示当前尚无该版本记录。
SELECT
    version AS migration_version, -- 中文说明：Flyway 迁移版本
    description AS migration_description, -- 中文说明：Flyway 迁移说明
    success AS migration_succeeded, -- 中文说明：该迁移是否成功
    installed_on AS migration_time -- 中文说明：迁移记录时间
FROM flyway_schema_history
WHERE version = '2026100901'; -- 中文说明：本次数据库结构清晰化迁移版本

-- 中文说明：预览旧举报总数以及尚未进入统一队列的数量；所有旧记录都会复制到归档表。
SELECT
    COUNT(*) AS legacy_report_rows, -- 中文说明：旧 meme_report 总行数，将全部进入归档表
    SUM(CASE WHEN current_report.id IS NULL THEN 1 ELSE 0 END) AS community_rows_to_insert -- 中文说明：将补入统一举报队列的行数
FROM meme_report AS legacy_report
LEFT JOIN community_report AS current_report
  ON current_report.target_type = 'MEME' -- 中文说明：梗举报类型
 AND current_report.target_id = legacy_report.meme_id -- 中文说明：目标梗ID匹配
 AND current_report.reporter_user_id = legacy_report.reporter_user_id; -- 中文说明：举报人匹配

-- 中文说明：预览没有对应梗记录的标签关联；迁移会删除这些无效关联行。
SELECT
    relation_row.meme_id AS orphan_meme_id, -- 中文说明：找不到梗主记录的关联字段
    relation_row.meme_tag_id AS related_tag_id -- 中文说明：该无效关联引用的标签ID
FROM meme_tag_relation AS relation_row
LEFT JOIN meme AS meme_row ON meme_row.id = relation_row.meme_id -- 中文说明：按梗主键查找目标记录
WHERE meme_row.id IS NULL; -- 中文说明：只筛选不存在目标梗的行

-- 中文说明：预览没有对应标签记录的标签关联；迁移会删除这些无效关联行。
SELECT
    relation_row.meme_id AS related_meme_id, -- 中文说明：该关联引用的梗ID
    relation_row.meme_tag_id AS orphan_tag_id -- 中文说明：找不到标签主记录的关联字段
FROM meme_tag_relation AS relation_row
LEFT JOIN meme_tag AS tag_row ON tag_row.id = relation_row.meme_tag_id -- 中文说明：按标签主键查找目标记录
WHERE tag_row.id IS NULL; -- 中文说明：只筛选不存在目标标签的行

-- 中文说明：预览同时存在有效行和软删除行的点赞组合；迁移保留有效行并删除删除态副本。
SELECT
    user_id, -- 中文说明：点赞用户ID
    meme_id, -- 中文说明：被点赞梗ID
    SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS active_rows, -- 中文说明：有效点赞行数
    SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deleted_rows -- 中文说明：软删除点赞行数
FROM user_like
GROUP BY user_id, meme_id
HAVING active_rows > 0 AND deleted_rows > 0;

-- 中文说明：预览同时存在有效行和软删除行的收藏组合；迁移保留有效行并删除删除态副本。
SELECT
    user_id, -- 中文说明：收藏用户ID
    meme_id, -- 中文说明：被收藏梗ID
    SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS active_rows, -- 中文说明：有效收藏行数
    SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deleted_rows -- 中文说明：软删除收藏行数
FROM user_favorite
GROUP BY user_id, meme_id
HAVING active_rows > 0 AND deleted_rows > 0;

-- 中文说明：以下查询只显示计数差异，不会写入数据库。

-- 中文说明：核对梗点赞累计数与未删除点赞明细。
SELECT
    meme_row.id AS meme_id, -- 中文说明：梗主键
    meme_row.likes AS stored_likes, -- 中文说明：梗表当前点赞冗余计数
    COALESCE(like_rows.active_like_count, 0) AS source_likes -- 中文说明：未删除点赞明细数
FROM meme AS meme_row
LEFT JOIN (
    SELECT
        meme_id, -- 中文说明：点赞明细所属梗ID
        COUNT(*) AS active_like_count -- 中文说明：该梗未删除的点赞记录数
    FROM user_like
    WHERE is_deleted = 0
    GROUP BY meme_id
) AS like_rows ON like_rows.meme_id = meme_row.id
WHERE COALESCE(meme_row.likes, 0) <> COALESCE(like_rows.active_like_count, 0);

-- 中文说明：核对梗评论累计数；当前服务不因软删除而扣减，所以统计全部评论行。
SELECT
    meme_row.id AS meme_id, -- 中文说明：梗主键
    meme_row.comments AS stored_comments, -- 中文说明：梗表当前评论冗余计数
    COALESCE(comment_rows.total_comment_count, 0) AS source_comments -- 中文说明：该梗全部评论记录数，包含软删除行
FROM meme AS meme_row
LEFT JOIN (
    SELECT
        meme_id, -- 中文说明：评论所属梗ID
        COUNT(*) AS total_comment_count -- 中文说明：该梗全部评论行数
    FROM meme_comment
    GROUP BY meme_id
) AS comment_rows ON comment_rows.meme_id = meme_row.id
WHERE COALESCE(meme_row.comments, 0) <> COALESCE(comment_rows.total_comment_count, 0);

-- 中文说明：核对用户统计表；发布梗数按当前可见状态 status=1 计算。
WITH published_meme_counts AS (
    SELECT
        user_id, -- 中文说明：梗发布者用户ID
        COUNT(*) AS published_meme_count -- 中文说明：状态为正常发布的梗数量
    FROM meme
    WHERE status = 1
    GROUP BY user_id
), received_like_counts AS (
    SELECT
        meme_row.user_id, -- 中文说明：被点赞梗的作者用户ID
        COUNT(*) AS received_like_count -- 中文说明：作者所有梗收到的有效点赞数量
    FROM user_like AS like_row
    JOIN meme AS meme_row ON meme_row.id = like_row.meme_id
    WHERE like_row.is_deleted = 0
    GROUP BY meme_row.user_id
), favorite_counts AS (
    SELECT
        user_id, -- 中文说明：收藏者用户ID
        COUNT(*) AS active_favorite_count -- 中文说明：有效收藏记录数
    FROM user_favorite
    WHERE is_deleted = 0
    GROUP BY user_id
), follow_counts AS (
    SELECT
        from_user_id AS user_id, -- 中文说明：关注发起人用户ID
        COUNT(*) AS active_follow_count -- 中文说明：有效关注关系数
    FROM user_relation
    WHERE is_deleted = 0
    GROUP BY from_user_id
), fan_counts AS (
    SELECT
        to_user_id AS user_id, -- 中文说明：被关注用户ID
        COUNT(*) AS active_fan_count -- 中文说明：有效粉丝关系数
    FROM user_relation
    WHERE is_deleted = 0
    GROUP BY to_user_id
)
SELECT
    user_row.id AS user_id, -- 中文说明：用户主键
    COALESCE(stats.meme_count, 0) AS stored_meme_count, -- 中文说明：统计表当前发布梗数
    COALESCE(published.published_meme_count, 0) AS source_meme_count, -- 中文说明：梗表中 status=1 的记录数
    COALESCE(stats.like_received, 0) AS stored_like_received, -- 中文说明：统计表当前获赞数
    COALESCE(received.received_like_count, 0) AS source_like_received, -- 中文说明：有效点赞明细聚合数
    COALESCE(stats.favorite_count, 0) AS stored_favorite_count, -- 中文说明：统计表当前收藏数
    COALESCE(favorites.active_favorite_count, 0) AS source_favorite_count, -- 中文说明：有效收藏明细数
    COALESCE(stats.follow_count, 0) AS stored_follow_count, -- 中文说明：统计表当前关注数
    COALESCE(follows.active_follow_count, 0) AS source_follow_count, -- 中文说明：有效关注关系数
    COALESCE(stats.fans_count, 0) AS stored_fans_count, -- 中文说明：统计表当前粉丝数
    COALESCE(fans.active_fan_count, 0) AS source_fans_count -- 中文说明：有效粉丝关系数
FROM user AS user_row
LEFT JOIN user_stats AS stats ON stats.user_id = user_row.id
LEFT JOIN published_meme_counts AS published ON published.user_id = user_row.id
LEFT JOIN received_like_counts AS received ON received.user_id = user_row.id
LEFT JOIN favorite_counts AS favorites ON favorites.user_id = user_row.id
LEFT JOIN follow_counts AS follows ON follows.user_id = user_row.id
LEFT JOIN fan_counts AS fans ON fans.user_id = user_row.id
WHERE COALESCE(stats.meme_count, 0) <> COALESCE(published.published_meme_count, 0)
   OR COALESCE(stats.like_received, 0) <> COALESCE(received.received_like_count, 0)
   OR COALESCE(stats.favorite_count, 0) <> COALESCE(favorites.active_favorite_count, 0)
   OR COALESCE(stats.follow_count, 0) <> COALESCE(follows.active_follow_count, 0)
   OR COALESCE(stats.fans_count, 0) <> COALESCE(fans.active_fan_count, 0);

-- 中文说明：核对非默认收藏夹数量；默认夹使用 folder_id=0，没有对应收藏夹记录。
SELECT
    folder_row.id AS folder_id, -- 中文说明：收藏夹主键
    folder_row.user_id AS owner_user_id, -- 中文说明：收藏夹所属用户ID
    folder_row.meme_count AS stored_meme_count, -- 中文说明：收藏夹当前冗余梗数量
    COUNT(favorite_row.id) AS source_meme_count -- 中文说明：该收藏夹中的有效收藏明细数
FROM user_favorite_folder AS folder_row
LEFT JOIN user_favorite AS favorite_row
  ON favorite_row.folder_id = folder_row.id
 AND favorite_row.is_deleted = 0
WHERE folder_row.is_deleted = 0
GROUP BY folder_row.id, folder_row.user_id, folder_row.meme_count
HAVING folder_row.meme_count <> COUNT(favorite_row.id);
