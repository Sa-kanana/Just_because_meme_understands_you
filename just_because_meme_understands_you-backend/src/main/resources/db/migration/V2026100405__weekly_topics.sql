-- ISO 周主题快照。首次读取时固化该周选出的标签，周内保持一致。
CREATE TABLE IF NOT EXISTS meme_weekly_topic
(
    iso_week_year     SMALLINT    NOT NULL COMMENT 'ISO 周对应的年份',
    iso_week_number   TINYINT     NOT NULL COMMENT 'ISO 周号',
    tag_id            INT         NOT NULL COMMENT '选中的标签 ID',
    tag_name          VARCHAR(255) NOT NULL COMMENT '标签名快照',
    interaction_score BIGINT      NOT NULL DEFAULT 0 COMMENT '近 7 天互动增长分',
    is_fallback       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否因趋势历史不足而回退',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '主题确定时间',
    PRIMARY KEY (iso_week_year, iso_week_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每周热梗主题';
