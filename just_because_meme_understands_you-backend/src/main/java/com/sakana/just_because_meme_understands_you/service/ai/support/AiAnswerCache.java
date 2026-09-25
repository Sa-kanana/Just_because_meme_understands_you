package com.sakana.just_because_meme_understands_you.service.ai.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        String key = buildKey(userId, query);
        String value = redisTemplate.opsForValue().get(key);
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }

    public void put(Long userId, String query, String answer) {
        if (!StringUtils.hasText(answer)) {
            return;
        }
        String key = buildKey(userId, query);
        long ttl = Math.max(1L, properties.getAi().getCacheTtlSeconds());
        redisTemplate.opsForValue().set(key, answer, Duration.ofSeconds(ttl));
    }

    public void evict(Long userId, String query) {
        redisTemplate.delete(buildKey(userId, query));
    }

    /**
     * 生成缓存 Key  固定长度 MD5（userid+query）
     * @param userId
     * @param query
     * @return
     */
    private String buildKey(Long userId, String query) {
        //归一化：去空格 + 转小写
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        //拼接用户 ID + 归一化后的查询字符串
        String raw = userId + "|" + normalized;
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
