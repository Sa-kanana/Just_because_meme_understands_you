package com.sakana.just_because_meme_understands_you.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MemeAgent（FastAPI）与 AI 搜索业务参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meme")
public class MemeAgentProperties {

    private Agent agent = new Agent();
    private Ai ai = new Ai();

    @Data
    public static class Agent {
        private String baseUrl = "http://127.0.0.1:8000";
        private String apiKey = "";
        private long connectTimeoutMs = 2000L;
        private long responseTimeoutMs = 60000L;
        private String streamPath = "/stream";
        private String ingestPath = "/ingest";
    }

    @Data
    public static class Ai {
        private int maxHistoryTokens = 3000;
        private int maxOutputTokens = 512;
        private long cacheTtlSeconds = 120L;
        private int rateLimitPerMinute = 20;
        /** 保守估算：每 token 约等于多少字符（中文场景） */
        private int charsPerTokenEstimate = 3;
    }
}
