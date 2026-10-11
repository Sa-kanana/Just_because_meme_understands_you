package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * MemeAgent（FastAPI）与 AI 搜索业务参数。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "meme")
public class MemeAgentProperties {

    @Valid
    private Agent agent = new Agent();
    @Valid
    private Ai ai = new Ai();

    @Data
    public static class Agent {
        private String baseUrl = "http://127.0.0.1:8000";
        /** 只读密钥：调用 /stream */
        private String apiKey = "";
        /**
         * 写密钥：调用 /ingest、/crawl；为空则回退 apiKey（仅建议开发）。
         */
        private String writeApiKey = "";
        private long connectTimeoutMs = 2000L;
        /** MemeAgent 响应读取超时时间（毫秒），默认 20 分钟。 */
        private long responseTimeoutMs = 1200000L;
        /** MemeAgent SSE 上游连接池的最大活跃连接数。 */
        private int maxConnections = 100;
        /** 等待上游连接池连接的最大排队请求数，避免无限堆积。 */
        private int pendingAcquireMaxCount = 200;
        /** 等待上游空闲连接的最长时间，超时后由现有 SSE 错误流程处理。 */
        private long pendingAcquireTimeoutMs = 10000L;
        private String streamPath = "/stream";
        private String ingestPath = "/ingest";
        private String knowledgeIngestPath = "/ingest/knowledge";
        /** 知识索引 outbox 并行 worker 数，避免单线程串行等待模型向量服务。 */
        @Min(1)
        @Max(8)
        private int knowledgeIndexConcurrency = 2;
        private String crawlPath = "/crawl/hot-memes";
    }

    @Data
    public static class Ai {
        private int maxHistoryTokens = 10000;
        private int maxOutputTokens = 512;
        private long cacheTtlSeconds = 24 * 60 * 60L;
        /** AI SSE 两个事件之间允许空闲的最长时间 */
        private long streamIdleTimeoutMs = 130000L;
        /** AI 后台流式任务及其可回放事件的保留时间（秒）。 */
        private long taskTtlSeconds = 600L;
        private int rateLimitPerMinute = 20;
        /** 保守估算：每 token 约等于多少字符（中文场景） */
        private int charsPerTokenEstimate = 3;
        /** 是否允许 POST /ai/ingest/backfill 回填向量库 */
        private boolean backfillEnabled = true;
        /** MySQL 关键词预检索写入 hint 的条数上限（由 Assembler 使用时可覆盖） */
        private int mysqlHintLimit = 8;
    }

}
