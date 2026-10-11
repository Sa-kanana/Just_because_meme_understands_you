package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 在当前业务事务中持久化集成事件。 */
@Component
public class OutboxEventWriter {

    private final OutboxEventMapper mapper;

    public OutboxEventWriter(OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    /** 写入梗图索引事件，调用方事务回滚时事件同步回滚。 */
    public void appendMemeIndexEvent(long memeId, String eventType) {
        appendEvent("MEME", memeId, eventType, "{\"memeId\":" + memeId + "}");
    }

    /** 在调用方事务中写入通用集成事件。 */
    public Long appendEvent(String aggregateType, long aggregateId, String eventType, String payload) {
        if (!StringUtils.hasText(aggregateType) || aggregateId <= 0
                || !StringUtils.hasText(eventType) || !StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("Outbox 事件字段不完整");
        }
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType.trim());
        event.setAggregateId(aggregateId);
        event.setEventType(eventType.trim());
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(LocalDateTime.now());
        event.setNextAttemptAt(event.getCreatedAt());
        mapper.insert(event);
        return event.getId();
    }
}
