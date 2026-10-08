package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.application.AiSearchPreparation;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiSearchService;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskEvent;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskStatus;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.task.AiStreamTaskRedisStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStreamTaskServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 99L;
    private static final String REQUEST_ID = "request-1";

    @Mock
    private AiStreamTaskRedisStore store;
    @Mock
    private IAiSearchService searchService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RTopic cancelTopic;

    private MemeAgentProperties properties;
    private VirtualTimeScheduler expiryScheduler;
    private AiStreamTaskServiceImpl service;
    private final AtomicLong eventIds = new AtomicLong();

    @BeforeEach
    void setUp() {
        properties = new MemeAgentProperties();
        expiryScheduler = VirtualTimeScheduler.create();
        when(redissonClient.getTopic(anyString())).thenReturn(cancelTopic);
        lenient().when(cancelTopic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(1);
        service = new AiStreamTaskServiceImpl(store, searchService, properties, redissonClient, expiryScheduler);
        lenient().when(store.reserve(eq(USER_ID), eq(REQUEST_ID), anyString(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    long now = invocation.getArgument(3);
                    long expiry = invocation.getArgument(4);
                    String taskId = invocation.getArgument(2);
                    return new AiStreamTaskRedisStore.Reservation(true, snapshot(
                            taskId, AiStreamTaskStatus.RUNNING, now, expiry, 0L));
                });
        lenient().when(store.appendEvent(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> eventIds.incrementAndGet());
        lenient().when(store.appendTerminalEvent(anyString(), any(AiStreamTaskStatus.class), anyString(), anyString()))
                .thenAnswer(invocation -> eventIds.incrementAndGet());
        lenient().when(store.transition(anyString(), any(AiStreamTaskStatus.class))).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        expiryScheduler.dispose();
    }

    @Test
    void detachedWorkerContinuesAfterEventsSubscriberCancels() throws Exception {
        AtomicReference<FluxSink<ServerSentEvent<String>>> source = new AtomicReference<>();
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        CountDownLatch eventStored = new CountDownLatch(1);
        CountDownLatch readerStarted = new CountDownLatch(1);
        AiStreamTaskSnapshot task = submitWithSource(source, sourceCancelled);
        when(store.find(task.taskId())).thenReturn(Optional.of(task));
        doAnswer(invocation -> {
            readerStarted.countDown();
            return List.of();
        }).when(store).readEvents(task.taskId(), 0L, 64);
        doAnswer(invocation -> {
            eventStored.countDown();
            return eventIds.incrementAndGet();
        }).when(store).appendEvent(anyString(), eq("token"), anyString());

        var reader = service.events(USER_ID, task.taskId(), 0L).subscribe();
        assertTrue(readerStarted.await(2, TimeUnit.SECONDS));
        reader.dispose();
        source.get().next(event("token", "{\"text\":\"仍在运行\"}"));

        assertTrue(eventStored.await(2, TimeUnit.SECONDS));
        assertFalse(sourceCancelled.get());
    }

    @Test
    void reconnectReplaysOnlyEventsAfterLastId() {
        AiStreamTaskSnapshot task = snapshot("task-replay", AiStreamTaskStatus.COMPLETED,
                1_000L, 601_000L, 4L);
        when(store.find(task.taskId())).thenReturn(Optional.of(task));
        when(store.readEvents(task.taskId(), 2L, 64)).thenReturn(List.of(
                new AiStreamTaskEvent(3L, "token", "{\"text\":\"B\"}"),
                new AiStreamTaskEvent(4L, "done", "{}")));

        List<ServerSentEvent<String>> events = service.events(USER_ID, task.taskId(), 2L)
                .collectList().block(Duration.ofSeconds(3));

        assertNotNull(events);
        assertEquals(List.of("3", "4"), events.stream().map(ServerSentEvent::id).toList());
        assertEquals(List.of("token", "done"), events.stream().map(ServerSentEvent::event).toList());
        assertEquals("{\"text\":\"B\"}", events.get(0).data());
        verify(store).readEvents(task.taskId(), 2L, 64);
    }

    @Test
    void eventReaderTimesOutAfterConfiguredIdleWindow() {
        properties.getAi().setStreamIdleTimeoutMs(20L);
        long now = System.currentTimeMillis();
        AiStreamTaskSnapshot task = snapshot("task-idle", AiStreamTaskStatus.RUNNING,
                now, now + 600_000L, 0L);
        when(store.find(task.taskId())).thenReturn(Optional.of(task));
        when(store.readEvents(task.taskId(), 0L, 64)).thenReturn(List.of());

        StepVerifier.create(service.events(USER_ID, task.taskId(), 0L))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void cancelDisposesPythonSubscription() {
        AtomicReference<FluxSink<ServerSentEvent<String>>> source = new AtomicReference<>();
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        AiStreamTaskSnapshot task = submitWithSource(source, sourceCancelled);
        when(store.find(task.taskId())).thenReturn(Optional.of(task));

        AiStreamTaskSnapshot cancelled = service.cancel(USER_ID, task.taskId());

        assertEquals(AiStreamTaskStatus.CANCELLED, cancelled.status());
        assertTrue(sourceCancelled.get());
        verify(store).transition(task.taskId(), AiStreamTaskStatus.CANCELLED);
        verify(cancelTopic).publish(task.taskId());
    }

    @Test
    void ttlDisposesWorkerAndMarksExpired() {
        AtomicReference<FluxSink<ServerSentEvent<String>>> source = new AtomicReference<>();
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        AiStreamTaskSnapshot task = submitWithSource(source, sourceCancelled);

        expiryScheduler.advanceTimeBy(Duration.ofSeconds(600));

        assertTrue(sourceCancelled.get());
        verify(store).transition(task.taskId(), AiStreamTaskStatus.EXPIRED);
    }

    @Test
    void doneIsPublishedOnlyAfterAnswerPersistenceCompletes() throws Exception {
        AtomicReference<FluxSink<ServerSentEvent<String>>> source = new AtomicReference<>();
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        AtomicBoolean persistenceFinished = new AtomicBoolean();
        CountDownLatch doneStored = new CountDownLatch(1);
        AiStreamTaskSnapshot task = submitWithSource(source, sourceCancelled);
        doAnswer(invocation -> {
            assertTrue(persistenceFinished.get());
            assertEquals(AiStreamTaskStatus.COMPLETED, invocation.getArgument(1));
            assertEquals("done", invocation.getArgument(2));
            doneStored.countDown();
            return eventIds.incrementAndGet();
        }).when(store).appendTerminalEvent(
                eq(task.taskId()), eq(AiStreamTaskStatus.COMPLETED), eq("done"), anyString());

        source.get().next(event("token", "{\"text\":\"完整\"}"));
        source.get().next(event("done", "{\"finish_reason\":\"stop\"}"));
        verify(store, after(100).never()).appendTerminalEvent(
                eq(task.taskId()), eq(AiStreamTaskStatus.COMPLETED), eq("done"), anyString());

        persistenceFinished.set(true);
        source.get().complete();

        assertTrue(doneStored.await(2, TimeUnit.SECONDS));
        verify(store).appendTerminalEvent(
                eq(task.taskId()), eq(AiStreamTaskStatus.COMPLETED), eq("done"), anyString());
    }

    @Test
    void firstTerminalTransitionWinsWhenCancelRacesWithDone() {
        AtomicReference<FluxSink<ServerSentEvent<String>>> source = new AtomicReference<>();
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        AtomicReference<AiStreamTaskStatus> terminal = new AtomicReference<>(AiStreamTaskStatus.RUNNING);
        AiStreamTaskSnapshot task = submitWithSource(source, sourceCancelled);
        when(store.transition(task.taskId(), AiStreamTaskStatus.CANCELLED)).thenAnswer(invocation ->
                terminal.compareAndSet(AiStreamTaskStatus.RUNNING, AiStreamTaskStatus.CANCELLED));
        lenient().when(store.appendTerminalEvent(
                task.taskId(), AiStreamTaskStatus.COMPLETED, "done", "{}"))
                .thenAnswer(invocation -> terminal.compareAndSet(
                        AiStreamTaskStatus.RUNNING, AiStreamTaskStatus.COMPLETED)
                        ? eventIds.incrementAndGet()
                        : 0L);
        when(store.find(task.taskId())).thenReturn(Optional.of(task));

        service.cancel(USER_ID, task.taskId());
        source.get().next(event("done", "{}"));
        source.get().complete();

        assertEquals(AiStreamTaskStatus.CANCELLED, terminal.get());
        verify(store, never()).appendTerminalEvent(
                task.taskId(), AiStreamTaskStatus.COMPLETED, "done", "{}");
    }

    private AiStreamTaskSnapshot submitWithSource(
            AtomicReference<FluxSink<ServerSentEvent<String>>> source,
            AtomicBoolean sourceCancelled) {
        when(searchService.prepareSearch(eq(USER_ID), any(AiSearchStreamRequestDTO.class)))
                .thenAnswer(invocation -> {
                    AiSearchStreamRequestDTO request = invocation.getArgument(1);
                    return new AiSearchPreparation(SESSION_ID, request.getRequestId(), Flux.create(sink -> {
                        source.set(sink);
                        sink.onCancel(() -> sourceCancelled.set(true));
                    }));
                });
        AiSearchStreamRequestDTO request = new AiSearchStreamRequestDTO();
        request.setQuery("梗搜索");
        request.setRequestId(REQUEST_ID);
        return service.submit(USER_ID, request);
    }

    private static AiStreamTaskSnapshot snapshot(
            String taskId, AiStreamTaskStatus status, long createdAt, long expiresAt, long lastEventId) {
        return new AiStreamTaskSnapshot(
                taskId, USER_ID, SESSION_ID, REQUEST_ID, status, createdAt, expiresAt, lastEventId);
    }

    private static ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.<String>builder().event(name).data(data).build();
    }
}
