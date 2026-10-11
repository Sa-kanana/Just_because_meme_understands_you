-- 中文说明：记录通知来源 Outbox 事件，保证通知消费者重复投递时幂等。
ALTER TABLE user_notification
    ADD COLUMN source_event_id BIGINT UNSIGNED NULL
        COMMENT '来源 Outbox 事件 ID；为空表示非 Outbox 通知' AFTER group_key,
    ADD UNIQUE KEY uk_user_notification_source_event (source_event_id);
