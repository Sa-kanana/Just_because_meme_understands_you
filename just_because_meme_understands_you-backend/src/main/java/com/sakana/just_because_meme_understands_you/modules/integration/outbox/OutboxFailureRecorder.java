package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 在单一 MySQL 事务中记录 outbox 失败与知识文档索引状态。 */
@Service
public class OutboxFailureRecorder {

    private final OutboxEventMapper eventMapper;
    private final KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler;

    public OutboxFailureRecorder(OutboxEventMapper eventMapper,
                                 KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler) {
        this.eventMapper = eventMapper;
        this.knowledgeIndexOutboxHandler = knowledgeIndexOutboxHandler;
    }

    /** 仅当前租约持有者可以确认失败；返回 false 表示该租约已失效。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean record(OutboxEvent event,
                          String status,
                          int attempts,
                          LocalDateTime nextAttemptAt,
                          String error) {
        int updated = eventMapper.markFailed(
                event.getId(), event.getLockedAt(), status, attempts, nextAttemptAt, error);
        if (updated == 0) {
            return false;
        }

        if (KnowledgeIndexOutboxHandler.UPSERT_EVENT.equals(event.getEventType())
                || KnowledgeIndexOutboxHandler.DELETE_EVENT.equals(event.getEventType())) {
            if ("DEAD".equals(status)) {
                knowledgeIndexOutboxHandler.markDead(event, error);
            } else {
                knowledgeIndexOutboxHandler.markRetry(event, error);
            }
        }
        return true;
    }
}
