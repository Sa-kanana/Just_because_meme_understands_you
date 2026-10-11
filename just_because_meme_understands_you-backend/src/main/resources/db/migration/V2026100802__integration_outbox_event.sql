CREATE TABLE IF NOT EXISTS integration_outbox_event
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '发件箱事件主键',
    aggregate_type   VARCHAR(64)      NOT NULL COMMENT '业务聚合类型',
    aggregate_id     BIGINT UNSIGNED NOT NULL COMMENT '业务聚合标识',
    event_type       VARCHAR(100)     NOT NULL COMMENT '集成事件类型',
    payload          JSON             NOT NULL COMMENT '事件载荷快照',
    status           VARCHAR(20)      NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：待处理、处理中、重试、已完成或失败',
    attempts         INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
    created_at       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件创建时间',
    next_attempt_at  DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次允许投递时间',
    locked_at        DATETIME(3)               DEFAULT NULL COMMENT '本次处理租约时间',
    delivered_at     DATETIME(3)               DEFAULT NULL COMMENT '成功投递时间',
    last_error       VARCHAR(1000)             DEFAULT NULL COMMENT '最近一次投递错误摘要',
    PRIMARY KEY (id),
    KEY idx_outbox_status_retry (status, next_attempt_at, id),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '模块集成事务发件箱';
