package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskEvent;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 使用原子 Redis 脚本保存任务占用、事件顺序和终态。 */
@Slf4j
@Component
public class AiStreamTaskRedisStore {

    private static final String KEY_PREFIX = "meme:ai:stream:";
    private static final String EMPTY_KEY = KEY_PREFIX + "empty";

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local requestTask = redis.call('GET', KEYS[2])
            if requestTask then
                return {'IDEMPOTENT', requestTask}
            end
            local currentTask = redis.call('GET', KEYS[1])
            if currentTask then
                local currentMetaKey = ARGV[7] .. currentTask .. ':meta'
                if redis.call('HGET', currentMetaKey, 'status') == 'RUNNING' then
                    return {'CONFLICT', currentTask}
                end
            end
            redis.call('HSET', KEYS[3],
                'taskId', ARGV[1],
                'userId', ARGV[2],
                'sessionId', '',
                'requestId', ARGV[3],
                'status', 'RUNNING',
                'createdAtMillis', ARGV[4],
                'expiresAtMillis', ARGV[5],
                'lastEventId', '0')
            redis.call('PEXPIRE', KEYS[3], ARGV[6])
            redis.call('PSETEX', KEYS[1], ARGV[6], ARGV[1])
            redis.call('PSETEX', KEYS[2], ARGV[6], ARGV[1])
            return {'CREATED', ARGV[1]}
            """, List.class);

    private static final DefaultRedisScript<Long> APPEND_EVENT_SCRIPT = new DefaultRedisScript<>("""
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
                return -1
            end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return 0
            end
            local eventId = redis.call('HINCRBY', KEYS[1], 'lastEventId', 1)
            local encoded = cjson.encode({id = eventId, event = ARGV[1], data = ARGV[2]})
            redis.call('RPUSH', KEYS[2], encoded)
            redis.call('PEXPIRE', KEYS[2], ttl)
            return eventId
            """, Long.class);

    private static final DefaultRedisScript<Long> APPEND_TERMINAL_EVENT_SCRIPT = new DefaultRedisScript<>("""
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
                return -1
            end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return 0
            end
            local eventId = redis.call('HINCRBY', KEYS[1], 'lastEventId', 1)
            local encoded = cjson.encode({id = eventId, event = ARGV[2], data = ARGV[3]})
            redis.call('RPUSH', KEYS[2], encoded)
            redis.call('PEXPIRE', KEYS[2], ttl)
            redis.call('HSET', KEYS[1], 'status', ARGV[1])
            return eventId
            """, Long.class);

    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return 0
            end
            redis.call('HSET', KEYS[1], 'status', ARGV[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DELETE_TASK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
            end
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                redis.call('DEL', KEYS[2])
            end
            return redis.call('DEL', KEYS[3], KEYS[4])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AiStreamTaskRedisStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 原子登记任务；相同用户和请求 ID 返回旧任务，不同活动请求触发冲突。 */
    public Reservation reserve(Long userId,
                               String requestId,
                               String taskId,
                               long nowMillis,
                               long expiresAtMillis) {
        if (userId == null || !StringUtils.hasText(requestId) || !StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("userId、requestId 和 taskId 不能为空");
        }
        long ttlMillis = Math.max(1L, expiresAtMillis - nowMillis);
        List<String> keys = List.of(
                currentKey(userId),
                requestKey(userId, requestId),
                metadataKey(taskId),
                eventsKey(taskId));
        List<?> response = redisTemplate.execute(
                RESERVE_SCRIPT,
                keys,
                taskId,
                userId.toString(),
                requestId,
                Long.toString(nowMillis),
                Long.toString(expiresAtMillis),
                Long.toString(ttlMillis),
                KEY_PREFIX + "task:");
        if (response == null || response.size() < 2) {
            throw new IllegalStateException("Redis 未返回 AI 任务登记结果");
        }
        String disposition = String.valueOf(response.get(0));
        String selectedTaskId = String.valueOf(response.get(1));
        return switch (disposition) {
            case "CREATED" -> new Reservation(true, new AiStreamTaskSnapshot(
                    selectedTaskId,
                    userId,
                    null,
                    requestId,
                    AiStreamTaskStatus.RUNNING,
                    nowMillis,
                    expiresAtMillis,
                    0L));
            case "IDEMPOTENT" -> new Reservation(false, find(selectedTaskId)
                    .orElseThrow(() -> new IllegalStateException("幂等 AI 任务已过期")));
            case "CONFLICT" -> throw new ActiveTaskConflictException(selectedTaskId);
            default -> throw new IllegalStateException("未知 AI 任务登记结果: " + disposition);
        };
    }

    /** 查询用户最近一个仍在 TTL 内的任务，无论其是否已经结束。 */
    public Optional<AiStreamTaskSnapshot> findCurrent(Long userId) {
        String taskId = redisTemplate.opsForValue().get(currentKey(userId));
        return StringUtils.hasText(taskId) ? find(taskId) : Optional.empty();
    }

    /** 读取任务元数据。 */
    public Optional<AiStreamTaskSnapshot> find(String taskId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(metadataKey(taskId));
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        String id = field(fields, "taskId");
        String userId = field(fields, "userId");
        String requestId = field(fields, "requestId");
        String status = field(fields, "status");
        String createdAt = field(fields, "createdAtMillis");
        String expiresAt = field(fields, "expiresAtMillis");
        if (id == null || userId == null || requestId == null || status == null
                || createdAt == null || expiresAt == null) {
            log.warn("AI stream task metadata is incomplete taskId={}", taskId);
            return Optional.empty();
        }
        return Optional.of(new AiStreamTaskSnapshot(
                id,
                Long.valueOf(userId),
                parseNullableLong(field(fields, "sessionId")),
                requestId,
                AiStreamTaskStatus.valueOf(status),
                Long.parseLong(createdAt),
                Long.parseLong(expiresAt),
                parseLongOrDefault(field(fields, "lastEventId"), 0L)));
    }

    /** 写入准备阶段解析出的会话 ID，不刷新任务的原始过期时间。 */
    public void setSessionId(String taskId, Long sessionId) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(metadataKey(taskId)))) {
            throw new IllegalStateException("AI 任务已过期");
        }
        redisTemplate.opsForHash().put(metadataKey(taskId), "sessionId", String.valueOf(sessionId));
    }

    /** 由 Lua 原子增加事件 ID 并追加 JSON 事件，事件 TTL 与任务剩余 TTL 一致。 */
    public long appendEvent(String taskId, String event, String data) {
        Long eventId = redisTemplate.execute(
                APPEND_EVENT_SCRIPT,
                List.of(metadataKey(taskId), eventsKey(taskId)),
                event == null ? "message" : event,
                data == null ? "" : data);
        if (eventId == null || eventId <= 0L) {
            throw new IllegalStateException("AI 任务已结束或已过期，无法追加事件");
        }
        return eventId;
    }

    /** 原子写入终态事件并结束任务，避免 done 与取消竞态产生矛盾结果。 */
    public long appendTerminalEvent(
            String taskId, AiStreamTaskStatus terminalStatus, String event, String data) {
        if (terminalStatus == null || terminalStatus == AiStreamTaskStatus.RUNNING) {
            throw new IllegalArgumentException("只能追加 AI 任务终态事件");
        }
        Long eventId = redisTemplate.execute(
                APPEND_TERMINAL_EVENT_SCRIPT,
                List.of(metadataKey(taskId), eventsKey(taskId)),
                terminalStatus.name(),
                event == null ? "message" : event,
                data == null ? "" : data);
        return eventId != null && eventId > 0L ? eventId : 0L;
    }

    /** 按 SSE ID 游标读取后续有限数量的有序事件。 */
    public List<AiStreamTaskEvent> readEvents(String taskId, long afterId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        long cursor = Math.max(0L, afterId);
        long start = cursor;
        long end;
        try {
            end = Math.addExact(start, limit - 1L);
        } catch (ArithmeticException ignored) {
            end = Long.MAX_VALUE;
        }
        List<String> encodedEvents = redisTemplate.opsForList().range(eventsKey(taskId), start, end);
        if (encodedEvents == null || encodedEvents.isEmpty()) {
            return List.of();
        }
        List<AiStreamTaskEvent> events = new ArrayList<>(Math.min(limit, encodedEvents.size()));
        for (String encoded : encodedEvents) {
            try {
                AiStreamTaskEvent item = objectMapper.readValue(encoded, AiStreamTaskEvent.class);
                if (item.id() > cursor) {
                    events.add(item);
                    if (events.size() == limit) {
                        break;
                    }
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Redis 中的 AI 任务事件格式无效", exception);
            }
        }
        return List.copyOf(events);
    }

    /** 仅允许 RUNNING 状态转为首个终态；迟到的状态更新不会覆盖结果。 */
    public boolean transition(String taskId, AiStreamTaskStatus terminalStatus) {
        if (terminalStatus == null || terminalStatus == AiStreamTaskStatus.RUNNING) {
            throw new IllegalArgumentException("只能转换到 AI 任务终态");
        }
        Long changed = redisTemplate.execute(
                TRANSITION_SCRIPT,
                List.of(metadataKey(taskId)),
                terminalStatus.name());
        return Long.valueOf(1L).equals(changed);
    }

    /** 删除任务事件与元数据，并仅清除仍指向本任务的用户键。 */
    public void deleteTask(String taskId) {
        Optional<AiStreamTaskSnapshot> snapshot = find(taskId);
        String currentTaskKey = snapshot.map(item -> currentKey(item.userId())).orElse(EMPTY_KEY);
        String idempotencyKey = snapshot
                .map(item -> requestKey(item.userId(), item.requestId()))
                .orElse(EMPTY_KEY);
        redisTemplate.execute(
                DELETE_TASK_SCRIPT,
                List.of(currentTaskKey, idempotencyKey, metadataKey(taskId), eventsKey(taskId)),
                taskId);
    }

    private static String field(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long parseNullableLong(String value) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }

    private static long parseLongOrDefault(String value, long fallback) {
        return StringUtils.hasText(value) ? Long.parseLong(value) : fallback;
    }

    private static String currentKey(Long userId) {
        return KEY_PREFIX + "user:" + userId + ":current";
    }

    private static String requestKey(Long userId, String requestId) {
        return KEY_PREFIX + "user:" + userId + ":request:" + sha256(requestId);
    }

    private static String metadataKey(String taskId) {
        return KEY_PREFIX + "task:" + taskId + ":meta";
    }

    private static String eventsKey(String taskId) {
        return KEY_PREFIX + "task:" + taskId + ":events";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    /** 登记操作的返回值，标明本次请求是否创建了新任务。 */
    public record Reservation(
            /** 本次是否原子创建了新任务。 */
            boolean created,
            /** 已登记或复用的任务快照。 */
            AiStreamTaskSnapshot snapshot) {
    }

    /** 已有不同请求占用用户运行槽时抛出的冲突。 */
    public static class ActiveTaskConflictException extends RuntimeException {
        /** 当前占用该用户运行槽的任务 ID。 */
        private final String activeTaskId;

        public ActiveTaskConflictException(String activeTaskId) {
            super("该用户已有运行中的 AI 任务");
            this.activeTaskId = activeTaskId;
        }

        public String getActiveTaskId() {
            return activeTaskId;
        }
    }
}
