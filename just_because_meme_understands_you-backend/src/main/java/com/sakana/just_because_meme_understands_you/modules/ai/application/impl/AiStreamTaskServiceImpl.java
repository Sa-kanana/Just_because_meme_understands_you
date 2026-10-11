package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.application.AiSearchPreparation;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiSearchService;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiStreamTaskService;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskEvent;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskStatus;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.task.AiStreamTaskRedisStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 将 AI 搜索订阅从浏览器连接中剥离，并通过 Redis 提供安全回放。 */
@Slf4j
@Service
public class AiStreamTaskServiceImpl implements IAiStreamTaskService {

    private static final String CANCEL_TOPIC_NAME = "meme:ai:stream:cancel";
    private static final Duration EVENT_POLL_INTERVAL = Duration.ofMillis(250L);
    private static final int EVENT_BATCH_SIZE = 64;
    /** 用户停止生成时随终态 SSE 发送的安全错误码与结果文案。 */
    private static final String STOPPED_EVENT_DATA = "{\"code\":\"task_stopped\",\"message\":\"已停止生成\"}";
    /** 任务过期时随终态 SSE 发送的安全错误码与结果文案。 */
    private static final String EXPIRED_EVENT_DATA = "{\"code\":\"task_expired\",\"message\":\"搜索任务已过期\"}";
    private static final String GENERIC_ERROR_DATA = "{\"code\":\"upstream_error\","
            + "\"message\":\"AI 搜索服务暂时不可用，请稍后重试。\"}";

    /** Redis 中原子保存的任务和事件存储。 */
    private final AiStreamTaskRedisStore store;
    /** 执行并持久化 AI 搜索内容的应用服务。 */
    private final IAiSearchService searchService;
    /** 提供任务保留时间配置的 MemeAgent 属性。 */
    private final MemeAgentProperties properties;
    /** 跨 Java 实例广播任务取消信号的 Redis 主题。 */
    private final RTopic cancelTopic;
    /** 当前实例订阅取消主题时注册的监听器 ID。 */
    private final int cancelListenerId;
    /** 控制本机任务过期计时器的可注入调度器。 */
    private final Scheduler expiryScheduler;
    /** 本机正在消费的后台任务，不与任何 HTTP 读端绑定。 */
    private final ConcurrentMap<String, Worker> workers = new ConcurrentHashMap<>();
    /** 标记 Bean 关闭后拒绝启动新任务。 */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Autowired
    public AiStreamTaskServiceImpl(AiStreamTaskRedisStore store,
                                   IAiSearchService searchService,
                                   MemeAgentProperties properties,
                                   RedissonClient redissonClient) {
        this(store, searchService, properties, redissonClient, Schedulers.parallel());
    }

    public AiStreamTaskServiceImpl(AiStreamTaskRedisStore store,
                                  IAiSearchService searchService,
                                  MemeAgentProperties properties,
                                  RedissonClient redissonClient,
                                  Scheduler expiryScheduler) {
        this.store = store;
        this.searchService = searchService;
        this.properties = properties;
        this.expiryScheduler = expiryScheduler;
        this.cancelTopic = redissonClient.getTopic(CANCEL_TOPIC_NAME);
        this.cancelListenerId = cancelTopic.addListener(String.class,
                (channel, taskId) -> stopLocalWorker(taskId));
    }

    @Override
    public AiStreamTaskSnapshot submit(Long userId, AiSearchStreamRequestDTO request) {
        if (shuttingDown.get()) {
            throw new BizException(Result.CODE_ERROR, "AI 搜索服务正在关闭，请稍后重试");
        }
        String requestId = StringUtils.hasText(request.getRequestId())
                ? request.getRequestId().trim()
                : UUID.randomUUID().toString().replace("-", "");
        request.setRequestId(requestId);

        long nowMillis = System.currentTimeMillis();
        long ttlSeconds = Math.max(1L, properties.getAi().getTaskTtlSeconds());
        long expiresAtMillis = nowMillis + TimeUnit.SECONDS.toMillis(ttlSeconds);
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AiStreamTaskRedisStore.Reservation reservation;
        try {
            reservation = store.reserve(userId, requestId, taskId, nowMillis, expiresAtMillis);
        } catch (AiStreamTaskRedisStore.ActiveTaskConflictException conflict) {
            throw new BizException(
                    Result.CODE_TOO_MANY_REQUESTS,
                    "已有 AI 搜索正在生成，请等待完成或先停止当前任务");
        }
        if (!reservation.created()) {
            return reservation.snapshot();
        }

        try {
            AiSearchPreparation preparation = searchService.prepareSearch(userId, request);
            store.setSessionId(taskId, preparation.sessionId());
            AiStreamTaskSnapshot task = withSessionId(reservation.snapshot(), preparation.sessionId());
            startWorker(taskId, preparation.events(), expiresAtMillis);
            return task;
        } catch (RuntimeException exception) {
            failTask(taskId, exception);
            throw exception;
        }
    }

    @Override
    public Optional<AiStreamTaskSnapshot> getCurrent(Long userId) {
        return store.findCurrent(userId);
    }

    @Override
    public Flux<ServerSentEvent<String>> events(Long userId, String taskId, long afterEventId) {
        long cursor = Math.max(0L, afterEventId);
        return Mono.fromCallable(() -> requireOwnedTask(userId, taskId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ignored -> pollEvents(taskId, cursor))
                .timeout(Duration.ofMillis(Math.max(1L, properties.getAi().getStreamIdleTimeoutMs())));
    }

    @Override
    public AiStreamTaskSnapshot cancel(Long userId, String taskId) {
        AiStreamTaskSnapshot task = requireOwnedTask(userId, taskId);
        if (task.status() == AiStreamTaskStatus.STOPPED) {
            stopLocalWorker(taskId);
            publishCancellation(taskId);
            persistStoppedAnswer(task);
            return task;
        }
        if (task.status() != AiStreamTaskStatus.GENERATING) {
            return task;
        }
        long terminalEventId = store.appendTerminalEvent(
                taskId, AiStreamTaskStatus.STOPPED, "error", STOPPED_EVENT_DATA);
        if (terminalEventId <= 0L) {
            AiStreamTaskSnapshot currentTask = store.find(taskId).orElse(task);
            if (currentTask.status() == AiStreamTaskStatus.STOPPED) {
                stopLocalWorker(taskId);
                publishCancellation(taskId);
                persistStoppedAnswer(currentTask);
            }
            return currentTask;
        }
        stopLocalWorker(taskId);
        publishCancellation(taskId);
        persistStoppedAnswer(task);
        return withStatus(task, AiStreamTaskStatus.STOPPED);
    }

    @Override
    public void cancelBySession(Long userId, Long sessionId) {
        store.findCurrent(userId)
                .filter(task -> task.status() == AiStreamTaskStatus.GENERATING)
                .filter(task -> Objects.equals(task.sessionId(), sessionId))
                .ifPresent(task -> cancel(userId, task.taskId()));
    }

    private void startWorker(String taskId, Flux<ServerSentEvent<String>> events, long expiresAtMillis) {
        Worker worker = new Worker();
        Worker existing = workers.putIfAbsent(taskId, worker);
        if (existing != null) {
            throw new IllegalStateException("AI 任务已在当前实例启动");
        }
        long remainingMillis = Math.max(0L, expiresAtMillis - System.currentTimeMillis());
        worker.expiryTimer = expiryScheduler.schedule(
                () -> expireTask(taskId), remainingMillis, TimeUnit.MILLISECONDS);
        AtomicReference<ServerSentEvent<String>> pendingDone = new AtomicReference<>();
        Disposable subscription = events
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> handleEvent(taskId, pendingDone, event),
                        error -> failTask(taskId, error),
                        () -> completeTask(taskId, worker, pendingDone.get()));
        worker.subscription.update(subscription);
    }

    private void handleEvent(
            String taskId,
            AtomicReference<ServerSentEvent<String>> pendingDone,
            ServerSentEvent<String> event) {
        if (event == null) {
            return;
        }
        if ("done".equals(event.event())) {
            pendingDone.set(event);
            return;
        }
        try {
            if ("error".equals(event.event())) {
                String data = StringUtils.hasText(event.data()) ? event.data() : GENERIC_ERROR_DATA;
                store.appendTerminalEvent(taskId, AiStreamTaskStatus.FAILED, "error", data);
                stopLocalWorker(taskId);
                return;
            }
            store.appendEvent(taskId, event.event(), event.data());
        } catch (RuntimeException exception) {
            log.info("AI stream task stopped while appending event taskId={} errorType={}",
                    taskId, exception.getClass().getSimpleName());
            stopLocalWorker(taskId);
        }
    }

    private void completeTask(String taskId, Worker worker, ServerSentEvent<String> doneEvent) {
        try {
            if (doneEvent == null) {
                store.appendTerminalEvent(
                        taskId, AiStreamTaskStatus.FAILED, "error", GENERIC_ERROR_DATA);
            } else {
                store.appendTerminalEvent(
                        taskId,
                        AiStreamTaskStatus.COMPLETED,
                        doneEvent.event(),
                        doneEvent.data());
            }
        } catch (RuntimeException exception) {
            log.warn("AI stream task completion could not be recorded taskId={} errorType={}",
                    taskId, exception.getClass().getSimpleName());
        } finally {
            stopLocalWorker(taskId);
        }
    }

    private void failTask(String taskId, Throwable error) {
        try {
            store.appendTerminalEvent(
                    taskId, AiStreamTaskStatus.FAILED, "error", GENERIC_ERROR_DATA);
        } catch (RuntimeException recordingError) {
            log.warn("AI stream task failure could not be recorded taskId={} errorType={}",
                    taskId, recordingError.getClass().getSimpleName());
        }
        log.warn("AI stream task failed taskId={} errorType={}",
                taskId, error.getClass().getSimpleName());
        stopLocalWorker(taskId);
    }

    private void expireTask(String taskId) {
        try {
            long terminalEventId = store.appendTerminalEvent(
                    taskId, AiStreamTaskStatus.EXPIRED, "error", EXPIRED_EVENT_DATA);
            if (terminalEventId > 0L) {
                log.info("AI stream task expired taskId={}", taskId);
            }
        } catch (RuntimeException exception) {
            log.warn("AI stream task expiry could not be recorded taskId={} errorType={}",
                    taskId, exception.getClass().getSimpleName());
        } finally {
            stopLocalWorker(taskId);
            publishCancellation(taskId);
        }
    }

    private void publishCancellation(String taskId) {
        try {
            cancelTopic.publish(taskId);
        } catch (RuntimeException exception) {
            log.warn("AI stream cancellation notification failed taskId={} errorType={}",
                    taskId, exception.getClass().getSimpleName());
        }
    }

    private void persistStoppedAnswer(AiStreamTaskSnapshot task) {
        if (task.sessionId() == null) {
            return;
        }
        String content = store.readGeneratedAnswer(task.taskId());
        if (StringUtils.hasText(content)) {
            searchService.persistStoppedAnswer(task.sessionId(), task.requestId(), content);
        }
    }

    private void stopLocalWorker(String taskId) {
        Worker worker = workers.remove(taskId);
        if (worker != null) {
            worker.dispose();
        }
    }

    private AiStreamTaskSnapshot requireOwnedTask(Long userId, String taskId) {
        AiStreamTaskSnapshot task = store.find(taskId)
                .orElseThrow(() -> new BizException(Result.CODE_NOT_FOUND, "AI 搜索任务不存在或已过期"));
        if (!Objects.equals(task.userId(), userId)) {
            throw new BizException(Result.CODE_NOT_FOUND, "AI 搜索任务不存在或已过期");
        }
        if (task.status() == AiStreamTaskStatus.GENERATING
                && task.expiresAtMillis() <= System.currentTimeMillis()) {
            expireTask(taskId);
            return withStatus(task, AiStreamTaskStatus.EXPIRED);
        }
        return task;
    }

    private Flux<ServerSentEvent<String>> pollEvents(String taskId, long afterEventId) {
        AtomicLong cursor = new AtomicLong(afterEventId);
        return Flux.interval(Duration.ZERO, EVENT_POLL_INTERVAL, Schedulers.parallel())
                .concatMap(tick -> Mono.fromCallable(() -> readBatch(taskId, cursor.get()))
                        .subscribeOn(Schedulers.boundedElastic()))
                .takeUntil(EventBatch::terminalAndDrained)
                .concatMapIterable(EventBatch::events)
                .filter(event -> event.id() > cursor.get())
                .map(event -> {
                    cursor.set(event.id());
                    return ServerSentEvent.<String>builder()
                            .id(Long.toString(event.id()))
                            .event(event.event())
                            .data(event.data())
                            .build();
                });
    }

    private EventBatch readBatch(String taskId, long cursor) {
        Optional<AiStreamTaskSnapshot> snapshot = store.find(taskId);
        if (snapshot.isEmpty()) {
            return new EventBatch(List.of(), true);
        }
        boolean expired = snapshot.get().status() == AiStreamTaskStatus.GENERATING
                && snapshot.get().expiresAtMillis() <= System.currentTimeMillis();
        if (expired) {
            expireTask(taskId);
        }
        List<AiStreamTaskEvent> events = store.readEvents(taskId, cursor, EVENT_BATCH_SIZE);
        boolean terminal = expired || snapshot.get().status() != AiStreamTaskStatus.GENERATING;
        boolean drained = events.size() < EVENT_BATCH_SIZE;
        return new EventBatch(events, terminal && drained);
    }

    private static AiStreamTaskSnapshot withSessionId(AiStreamTaskSnapshot task, Long sessionId) {
        return new AiStreamTaskSnapshot(
                task.taskId(), task.userId(), sessionId, task.requestId(), task.status(),
                task.createdAtMillis(), task.expiresAtMillis(), task.lastEventId());
    }

    private static AiStreamTaskSnapshot withStatus(AiStreamTaskSnapshot task, AiStreamTaskStatus status) {
        return new AiStreamTaskSnapshot(
                task.taskId(), task.userId(), task.sessionId(), task.requestId(), status,
                task.createdAtMillis(), task.expiresAtMillis(), task.lastEventId());
    }

    @PreDestroy
    public void shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            workers.keySet().forEach(this::stopLocalWorker);
            cancelTopic.removeListener(cancelListenerId);
        }
    }

    /** 单个后台生成实例的订阅和 TTL 计时器。 */
    private static final class Worker {
        /** 可在 TTL、取消和正常终态间安全替换并释放的上游订阅。 */
        private final Disposable.Swap subscription = Disposables.swap();
        /** 到达任务创建期限时触发的取消计时器。 */
        private volatile Disposable expiryTimer = Disposables.disposed();

        private void dispose() {
            subscription.dispose();
            expiryTimer.dispose();
        }
    }

    /** 单轮 Redis 轮询返回的事件及是否已经读到终态末尾。 */
    private record EventBatch(
            /** 当前游标之后按序读取的 Redis 事件。 */
            List<AiStreamTaskEvent> events,
            /** 任务已终态且本轮已经读完全部事件。 */
            boolean terminalAndDrained) {
    }
}
