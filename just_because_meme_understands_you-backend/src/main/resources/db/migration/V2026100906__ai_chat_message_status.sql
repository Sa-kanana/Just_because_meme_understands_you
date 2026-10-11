-- 中文说明：为 AI 消息保存回答终态；历史消息和用户问题默认标记为已完成。
ALTER TABLE ai_chat_message
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
        COMMENT '消息状态：COMPLETED已完成、STOPPED用户停止、FAILED生成失败';
