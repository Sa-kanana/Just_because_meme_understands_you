-- 中文说明：本脚本只读，对比冗余计数与明细表，定位需要维护窗口校准的记录。
-- 中文说明：浏览量没有逐次明细，不能由本脚本精确重算；执行校准前先备份并核对业务口径。

-- 中文说明：比较 meme.likes 与有效 user_like 明细数；有效行定义为 is_deleted = 0。
SELECT
    meme_row.id AS meme_id, -- 字段说明：被核对梗的 meme.id
    meme_row.likes AS stored_count, -- 字段说明：目标字段 meme.likes 当前保存的点赞冗余计数
    (SELECT COUNT(*)
     FROM user_like AS like_row
     WHERE like_row.meme_id = meme_row.id AND like_row.is_deleted = 0) AS detail_count -- 字段说明：来源表 user_like 中该梗有效点赞行数
FROM meme AS meme_row
WHERE meme_row.likes <> (
    SELECT COUNT(*)
    FROM user_like AS like_row
    WHERE like_row.meme_id = meme_row.id AND like_row.is_deleted = 0
);

-- 中文说明：比较 meme.comments 与该梗的全部评论记录数，包含软删除评论以匹配累计评论语义。
SELECT
    meme_row.id AS meme_id, -- 字段说明：被核对梗的 meme.id
    meme_row.comments AS stored_count, -- 字段说明：目标字段 meme.comments 当前保存的累计评论数
    (SELECT COUNT(*)
     FROM meme_comment AS comment_row
     WHERE comment_row.meme_id = meme_row.id) AS detail_count -- 字段说明：来源表 meme_comment 中该梗的全部评论行数
FROM meme AS meme_row
WHERE meme_row.comments <> (
    SELECT COUNT(*)
    FROM meme_comment AS comment_row
    WHERE comment_row.meme_id = meme_row.id
);

-- 中文说明：比较 user_stats 中仍保留的冗余计数与明细表口径；发布梗数由 meme 表实时查询，不再冗余保存。
SELECT
    stats_row.user_id AS user_id, -- 字段说明：被核对用户的 user_stats.user_id
    stats_row.like_received AS stored_like_received, -- 字段说明：目标字段 user_stats.like_received 当前保存的获赞数
    (SELECT COUNT(*)
     FROM meme AS meme_row
     JOIN user_like AS like_row ON like_row.meme_id = meme_row.id AND like_row.is_deleted = 0
     WHERE meme_row.user_id = stats_row.user_id) AS actual_like_received, -- 字段说明：来源表 meme 与 user_like 汇总的有效获赞行数
    stats_row.favorite_count AS stored_favorite_count, -- 字段说明：目标字段 user_stats.favorite_count 当前保存的收藏数
    (SELECT COUNT(*)
     FROM user_favorite AS favorite_row
     WHERE favorite_row.user_id = stats_row.user_id AND favorite_row.is_deleted = 0) AS actual_favorite_count, -- 字段说明：来源表 user_favorite 中该用户有效收藏行数
    stats_row.follow_count AS stored_follow_count, -- 字段说明：目标字段 user_stats.follow_count 当前保存的关注数
    (SELECT COUNT(*)
     FROM user_relation AS relation_row
     WHERE relation_row.from_user_id = stats_row.user_id AND relation_row.is_deleted = 0) AS actual_follow_count, -- 字段说明：来源表 user_relation 中该用户发起的有效关注行数
    stats_row.fans_count AS stored_fans_count, -- 字段说明：目标字段 user_stats.fans_count 当前保存的粉丝数
    (SELECT COUNT(*)
     FROM user_relation AS relation_row
     WHERE relation_row.to_user_id = stats_row.user_id AND relation_row.is_deleted = 0) AS actual_fans_count -- 字段说明：来源表 user_relation 中指向该用户的有效关注行数
FROM user_stats AS stats_row
WHERE stats_row.like_received <> (
          SELECT COUNT(*)
          FROM meme AS meme_row
          JOIN user_like AS like_row ON like_row.meme_id = meme_row.id AND like_row.is_deleted = 0
          WHERE meme_row.user_id = stats_row.user_id
      )
   OR stats_row.favorite_count <> (
          SELECT COUNT(*) FROM user_favorite AS favorite_row
          WHERE favorite_row.user_id = stats_row.user_id AND favorite_row.is_deleted = 0
      )
   OR stats_row.follow_count <> (
          SELECT COUNT(*) FROM user_relation AS relation_row
          WHERE relation_row.from_user_id = stats_row.user_id AND relation_row.is_deleted = 0
      )
   OR stats_row.fans_count <> (
          SELECT COUNT(*) FROM user_relation AS relation_row
          WHERE relation_row.to_user_id = stats_row.user_id AND relation_row.is_deleted = 0
      );

-- 中文说明：比较具名收藏夹冗余计数与该夹中的有效收藏明细数；默认夹 folder_id=0 不在具名收藏夹表中。
SELECT
    folder_row.id AS folder_id, -- 字段说明：被核对收藏夹的 user_favorite_folder.id
    folder_row.user_id AS user_id, -- 字段说明：目标收藏夹所属用户ID
    folder_row.meme_count AS stored_count, -- 字段说明：目标字段 user_favorite_folder.meme_count 当前保存的收藏数
    COUNT(favorite_row.id) AS detail_count -- 字段说明：来源表 user_favorite 中该收藏夹有效收藏行数
FROM user_favorite_folder AS folder_row
LEFT JOIN user_favorite AS favorite_row
       ON favorite_row.user_id = folder_row.user_id
      AND favorite_row.folder_id = folder_row.id
      AND favorite_row.is_deleted = 0
GROUP BY folder_row.id, folder_row.user_id, folder_row.meme_count
HAVING folder_row.meme_count <> COUNT(favorite_row.id);
