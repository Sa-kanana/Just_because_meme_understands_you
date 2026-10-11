package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiIngestService;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 将持久化事件投递到对应模块应用契约，失败时按退避策略重试。 */
@Slf4j
@Component
public class OutboxEventRelay {

    private static final int BATCH_SIZE = 20;
    /** 中文注释：普通发件箱事件最多失败投递 12 次。 */
    private static final int MAX_ATTEMPTS = 12;
    /** 中文注释：知识索引任务首次失败后最多再自动重试 3 次。 */
    private static final int KNOWLEDGE_INDEX_MAX_RETRIES = 3;
    private static final long MAX_BACKOFF_SECONDS = 3600L;
    private static final int MAX_WORKER_CONCURRENCY = 8;

    private final OutboxClaimService claimService;
    private final OutboxEventMapper mapper;
    private final IAiIngestService aiIngestService;
    private final CommentNotificationOutboxHandler commentNotificationOutboxHandler;
    private final KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler;
    private final OutboxFailureRecorder outboxFailureRecorder;
    private final int workerConcurrency;
    private final ThreadPoolExecutor dispatchExecutor;

    @Autowired
    public OutboxEventRelay(
            OutboxClaimService claimService,
            OutboxEventMapper mapper,
            IAiIngestService aiIngestService,
            CommentNotificationOutboxHandler commentNotificationOutboxHandler,
            KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler,
            OutboxFailureRecorder outboxFailureRecorder,
            MemeAgentProperties memeAgentProperties) {
        this(claimService, mapper, aiIngestService, commentNotificationOutboxHandler,
                knowledgeIndexOutboxHandler, outboxFailureRecorder,
                memeAgentProperties.getAgent().getKnowledgeIndexConcurrency());
    }

    /** 兼容直接构造 relay 的调用方，生产环境通过配置构造并发 worker。 */
    public OutboxEventRelay(
            OutboxClaimService claimService,
            OutboxEventMapper mapper,
            IAiIngestService aiIngestService,
            CommentNotificationOutboxHandler commentNotificationOutboxHandler,
            KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler,
            OutboxFailureRecorder outboxFailureRecorder) {
        this(claimService, mapper, aiIngestService, commentNotificationOutboxHandler,
                knowledgeIndexOutboxHandler, outboxFailureRecorder, 2);
    }

    private OutboxEventRelay(
            OutboxClaimService claimService,
            OutboxEventMapper mapper,
            IAiIngestService aiIngestService,
            CommentNotificationOutboxHandler commentNotificationOutboxHandler,
            KnowledgeIndexOutboxHandler knowledgeIndexOutboxHandler,
            OutboxFailureRecorder outboxFailureRecorder,
            int configuredConcurrency) {
        this.claimService = claimService;
        this.mapper = mapper;
        this.aiIngestService = aiIngestService;
        this.commentNotificationOutboxHandler = commentNotificationOutboxHandler;
        this.knowledgeIndexOutboxHandler = knowledgeIndexOutboxHandler;
        this.outboxFailureRecorder = outboxFailureRecorder;
        this.workerConcurrency = Math.max(1, Math.min(MAX_WORKER_CONCURRENCY, configuredConcurrency));
        AtomicInteger workerNumber = new AtomicInteger();
        this.dispatchExecutor = new ThreadPoolExecutor(
                workerConcurrency,
                workerConcurrency,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workerConcurrency),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "outbox-dispatch-" + workerNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Scheduled(fixedDelayString = "${integration.outbox.fixed-delay-ms:5000}")
    public void relayPendingEvents() {
        relayOnce();
    }

    /** 执行一轮投递，返回成功处理的事件数，便于测试与运维观测。 */
    public synchronized int relayOnce() {
        long startedAt = System.nanoTime();
        List<OutboxEvent> events = claimService.claim(Math.min(BATCH_SIZE, workerConcurrency));
        if (events.isEmpty()) {
            return 0;
        }
        List<Future<Boolean>> outcomes = new ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            outcomes.add(dispatchExecutor.submit(() -> dispatchAndRecord(event)));
        }

        int delivered = 0;
        boolean interrupted = false;
        for (Future<Boolean> outcome : outcomes) {
            boolean finished = false;
            while (!finished) {
                try {
                    if (outcome.get()) {
                        delivered++;
                    }
                    finished = true;
                } catch (InterruptedException exception) {
                    // 中文注释：继续等待当前已领取事件完成，结束整轮后再恢复中断标志。
                    interrupted = true;
                } catch (ExecutionException exception) {
                    log.error("Outbox dispatch worker failed unexpectedly", exception.getCause());
                    finished = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        log.info("Outbox relay round completed claimed={} delivered={} workers={} elapsed_ms={}",
                events.size(), delivered, workerConcurrency,
                (System.nanoTime() - startedAt) / 1_000_000L);
        return delivered;
    }

    private boolean dispatchAndRecord(OutboxEvent event) {
        long startedAt = System.nanoTime();
        try {
            dispatch(event);
            boolean delivered = mapper.markDelivered(
                    event.getId(), event.getLockedAt(), LocalDateTime.now()) > 0;
            log.info("Outbox event completed id={} type={} delivered={} elapsed_ms={}",
                    event.getId(), event.getEventType(), delivered,
                    (System.nanoTime() - startedAt) / 1_000_000L);
            return delivered;
        } catch (Exception exception) {
            recordFailure(event, exception, (System.nanoTime() - startedAt) / 1_000_000L);
            return false;
        }
    }

    @PreDestroy
    public void shutdownDispatchExecutor() {
        dispatchExecutor.shutdown();
        try {
            if (!dispatchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void dispatch(OutboxEvent event) {
        if (MemeIndexOutboxAdapter.UPSERT_EVENT.equals(event.getEventType())
                || MemeIndexOutboxAdapter.REMOVE_EVENT.equals(event.getEventType())) {
            if (!aiIngestService.isAvailable()) {
                throw new IllegalStateException("AI 索引服务当前不可用，事件保留重试");
            }
            // 重新读取当前已提交状态，避免过期的删除事件覆盖后续重新发布。
            aiIngestService.synchronizeMemeIndex(event.getAggregateId());
        } else if (CommentNotificationOutboxAdapter.MEME_COMMENTED_EVENT.equals(event.getEventType())
                || CommentNotificationOutboxAdapter.COMMENT_REPLIED_EVENT.equals(event.getEventType())) {
            commentNotificationOutboxHandler.handle(event);
        } else if (KnowledgeIndexOutboxHandler.UPSERT_EVENT.equals(event.getEventType())
                || KnowledgeIndexOutboxHandler.DELETE_EVENT.equals(event.getEventType())) {
            knowledgeIndexOutboxHandler.handle(event);
        } else {
            throw new IllegalArgumentException("未支持的发件箱事件类型: " + event.getEventType());
        }
    }

    private void recordFailure(OutboxEvent event, Exception exception, long elapsedMs) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        int maxAttempts = isKnowledgeIndexEvent(event.getEventType())
                ? KNOWLEDGE_INDEX_MAX_RETRIES + 1
                : MAX_ATTEMPTS;
        String status = attempts >= maxAttempts ? "DEAD" : "RETRY";
        long backoff = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempts, 20));
        LocalDateTime nextAttempt = LocalDateTime.now().plusSeconds(backoff);
        String error = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        if (error.length() > 1000) {
            error = error.substring(0, 1000);
        }
        try {
            outboxFailureRecorder.record(event, status, attempts, nextAttempt, error);
        } catch (RuntimeException persistenceFailure) {
            // 中文注释：失败状态与文档状态同事务提交；记录失败时保留租约供后续超时恢复。
            log.error("无法持久化发件箱失败状态 id={} type={}", event.getId(), event.getEventType(), persistenceFailure);
        }
        log.warn("发件箱事件投递失败 id={} type={} attempt={} status={} elapsed_ms={}",
                event.getId(), event.getEventType(), attempts, status, elapsedMs, exception);
    }

    private static boolean isKnowledgeIndexEvent(String eventType) {
        return KnowledgeIndexOutboxHandler.UPSERT_EVENT.equals(eventType)
                || KnowledgeIndexOutboxHandler.DELETE_EVENT.equals(eventType);
    }
}
