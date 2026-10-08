package com.sakana.just_because_meme_understands_you.modules.ai.application.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * AI 答案短缓存（Java 侧，降低 LLM 费用）。
 */
@Component
public class AiAnswerCache {

    private static final String KEY_PREFIX = "meme:ai:answer:";

    private final StringRedisTemplate redisTemplate;
    private final MemeAgentProperties properties;
    private final ObjectMapper objectMapper;

    public AiAnswerCache(StringRedisTemplate redisTemplate,
                         MemeAgentProperties properties,
                         ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<String> get(Long userId, String query) {
        return get(userId, query, "");
    }

    public Optional<String> get(Long userId, String query, String contextFingerprint) {
        String key = buildKey(userId, query, contextFingerprint);
        String value = redisTemplate.opsForValue().get(key);
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }

    public void put(Long userId, String query, String answer) {
        put(userId, query, "", answer);
    }

    public void put(Long userId, String query, String contextFingerprint, String answer) {
        if (!StringUtils.hasText(answer)) {
            return;
        }
        String key = buildKey(userId, query, contextFingerprint);
        long ttl = Math.max(1L, properties.getAi().getCacheTtlSeconds());
        redisTemplate.opsForValue().set(key, answer, Duration.ofSeconds(ttl));
    }

    public void evict(Long userId, String query) {
        redisTemplate.delete(buildKey(userId, query, ""));
    }

    public void evict(Long userId, String query, String contextFingerprint) {
        redisTemplate.delete(buildKey(userId, query, contextFingerprint));
    }

    /**
     * 对参与答案生成的上下文做稳定摘要；缓存 Key 不暴露历史消息或业务片段。
     *
     * @param context 当前回答依赖的历史、业务数据和流水线版本
     * @return SHA-256 上下文指纹
     */
    public String fingerprint(Object context) {
        try {
            byte[] canonical = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(context);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("AI cache context fingerprint failed", e);
        }
    }

    /**
     * 生成缓存 Key：对 userid、归一化 query 和上下文指纹生成固定长度 MD5。
     * @param userId
     * @param query
     * @return
     */
    private String buildKey(Long userId, String query, String contextFingerprint) {
        //归一化：去空格 + 转小写
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        //拼接用户 ID、归一化后的查询字符串与上下文指纹
        String raw = userId + "|" + normalized + "|" + (contextFingerprint == null ? "" : contextFingerprint);
        // MD5 摘要（固定长度，避免 Key 过长）
        String digest = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + digest;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialize failed", e);
        }
    }
}
