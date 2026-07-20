-- AI 搜索会话（Java SSOT，Agent 不持久化）
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id           BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    user_id      BIGINT       NOT NULL COMMENT '所属用户',
    title        VARCHAR(128) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未删 1=已删',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ai_chat_session_user (user_id, is_deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 搜索会话';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    session_id     BIGINT       NOT NULL COMMENT '会话 ID',
    role           VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
    content        TEXT         NOT NULL COMMENT '消息正文',
    request_id     VARCHAR(64)  NULL COMMENT '关联一次流式请求',
    token_estimate INT          NULL COMMENT '估算 token 数',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_chat_message_session (session_id, create_time),
    KEY idx_ai_chat_message_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 搜索消息';
