package com.sakana.just_because_meme_understands_you.modules.ai.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class AiAnswerCacheTest {

    @Test
    void contextFingerprintIsStableForMapOrderAndChangesWithConversationContext() {
        AiAnswerCache cache = new AiAnswerCache(
                mock(StringRedisTemplate.class),
                new MemeAgentProperties(),
                new ObjectMapper());
        Map<String, Object> firstOrder = new LinkedHashMap<>();
        firstOrder.put("messages", List.of(Map.of("role", "user", "content", "上一问")));
        firstOrder.put("locale", "zh-CN");
        Map<String, Object> secondOrder = new LinkedHashMap<>();
        secondOrder.put("locale", "zh-CN");
        secondOrder.put("messages", List.of(Map.of("content", "上一问", "role", "user")));
        Map<String, Object> differentHistory = new LinkedHashMap<>(secondOrder);
        differentHistory.put("messages", List.of(Map.of("role", "user", "content", "另一段历史")));

        assertEquals(cache.fingerprint(firstOrder), cache.fingerprint(secondOrder));
        assertNotEquals(cache.fingerprint(firstOrder), cache.fingerprint(differentHistory));
    }
}
