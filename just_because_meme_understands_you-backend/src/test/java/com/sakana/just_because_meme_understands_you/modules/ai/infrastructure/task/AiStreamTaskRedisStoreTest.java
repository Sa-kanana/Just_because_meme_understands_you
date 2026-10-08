package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskEvent;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskStatus;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStreamTaskRedisStoreTest {

    private static final String TASK_ID = "task-existing";
    private static final long NOW_MILLIS = 1_000_000L;
    private static final long EXPIRES_AT_MILLIS = NOW_MILLIS + 600_000L;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ListOperations<String, String> listOperations;

    private MemeAgentProperties properties;
    private ObjectMapper objectMapper;
    private AiStreamTaskRedisStore store;

    @BeforeEach
    void setUp() {
        properties = new MemeAgentProperties();
        objectMapper = new ObjectMapper();
        store = new AiStreamTaskRedisStore(redisTemplate, objectMapper);
    }

    @Test
    void taskTtlDefaultsTo600Seconds() {
        assertEquals(600L, properties.getAi().getTaskTtlSeconds());
    }

    @Test
    void reserveIsIdempotentForSameUserAndRequestId() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        stubExistingSnapshot(TASK_ID);
        doReturn(List.of("IDEMPOTENT", TASK_ID))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        AiStreamTaskRedisStore.Reservation reservation = store.reserve(
                7L, "request-1", "new-task", NOW_MILLIS, EXPIRES_AT_MILLIS);

        assertFalse(reservation.created());
        assertEquals(TASK_ID, reservation.snapshot().taskId());
        assertEquals(AiStreamTaskStatus.RUNNING, reservation.snapshot().status());
    }

    @Test
    void reserveRejectsDifferentRequestWhileUserTaskRuns() {
        doReturn(List.of("CONFLICT", "running-task"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThrows(AiStreamTaskRedisStore.ActiveTaskConflictException.class,
                () -> store.reserve(7L, "request-2", "new-task", NOW_MILLIS, EXPIRES_AT_MILLIS));
    }

    @Test
    void readsOnlyEventsAfterCursor() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        List<String> encodedEvents = List.of(
                objectMapper.writeValueAsString(new AiStreamTaskEvent(3L, "token", "{\"text\":\"三\"}")),
                objectMapper.writeValueAsString(new AiStreamTaskEvent(4L, "done", "{}")));
        when(listOperations.range("meme:ai:stream:task:" + TASK_ID + ":events", 2L, 3L))
                .thenReturn(encodedEvents);

        List<AiStreamTaskEvent> events = store.readEvents(TASK_ID, 2L, 2);

        assertEquals(List.of(3L, 4L), events.stream().map(AiStreamTaskEvent::id).toList());
        verify(listOperations).range("meme:ai:stream:task:" + TASK_ID + ":events", 2L, 3L);
    }

    @Test
    void expiresAllTaskKeysAtConfiguredDeadline() {
        doAnswer(invocation -> {
            RedisScript<?> script = invocation.getArgument(0);
            if (script.getScriptAsString().contains("PSETEX")) {
                return List.of("CREATED", "new-task");
            }
            return 1L;
        }).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        store.reserve(7L, "request-3", "new-task", NOW_MILLIS, EXPIRES_AT_MILLIS);
        long eventId = store.appendEvent("new-task", "token", "{\"text\":\"结果\"}");

        assertEquals(1L, eventId);
        ArgumentCaptor<RedisScript> scripts = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(scripts.capture(), keys.capture(), any(Object[].class));
        Object[] reservationArguments = invocationArgumentsForReserve();
        assertEquals(600_000L, Long.parseLong(String.valueOf(reservationArguments[5])));
        assertTrue(scripts.getAllValues().get(0).getScriptAsString().contains("PSETEX"));
        assertTrue(scripts.getAllValues().get(0).getScriptAsString().contains("PEXPIRE"));
        assertTrue(keys.getAllValues().get(0).contains("meme:ai:stream:task:new-task:events"));
        assertTrue(scripts.getAllValues().get(1).getScriptAsString().contains("PTTL"));
        assertTrue(scripts.getAllValues().get(1).getScriptAsString().contains("RPUSH"));
        assertTrue(scripts.getAllValues().get(1).getScriptAsString().contains("PEXPIRE"));
    }

    @Test
    void terminalTransitionCannotOverwriteExistingTerminalState() {
        doReturn(0L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertFalse(store.transition(TASK_ID, AiStreamTaskStatus.CANCELLED));

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(script.capture(), anyList(), any(Object[].class));
        assertTrue(script.getValue().getScriptAsString().contains("RUNNING"));
    }

    private void stubExistingSnapshot(String taskId) {
        when(hashOperations.entries("meme:ai:stream:task:" + taskId + ":meta"))
                .thenReturn(Map.of(
                        "taskId", taskId,
                        "userId", "7",
                        "sessionId", "99",
                        "requestId", "request-1",
                        "status", "RUNNING",
                        "createdAtMillis", String.valueOf(NOW_MILLIS),
                        "expiresAtMillis", String.valueOf(EXPIRES_AT_MILLIS),
                        "lastEventId", "0"));
    }

    private Object[] invocationArgumentsForReserve() {
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertTrue(keys.getAllValues().get(0).contains("meme:ai:stream:user:7:current"));
        return arguments.getAllValues().get(0);
    }
}
