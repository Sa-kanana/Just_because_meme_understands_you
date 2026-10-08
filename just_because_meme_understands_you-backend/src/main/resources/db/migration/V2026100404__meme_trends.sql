-- 梗热度趋势快照。每天由 MemeTrendSnapshotTask 保存公开梗的计数基线。
CREATE TABLE IF NOT EXISTS meme_daily_snapshot
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '快照记录ID',
    meme_id       BIGINT      NOT NULL COMMENT '梗ID',
    snapshot_date DATE        NOT NULL COMMENT '快照日期',
    page_views    BIGINT      NOT NULL DEFAULT 0 COMMENT '快照时浏览总量',
    likes         BIGINT      NOT NULL DEFAULT 0 COMMENT '快照时点赞总量',
    comments      BIGINT      NOT NULL DEFAULT 0 COMMENT '快照时评论总量',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照创建时间',
    UNIQUE KEY uk_meme_snapshot_day (meme_id, snapshot_date),
    KEY idx_snapshot_date (snapshot_date, meme_id)
) COMMENT '梗每日互动计数快照';
