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
        /** 只读密钥：调用 /stream */
        private String apiKey = "";
        /**
         * 写密钥：调用 /ingest、/crawl；为空则回退 apiKey（仅建议开发）。
         */
        private String writeApiKey = "";
        private long connectTimeoutMs = 2000L;
        private long responseTimeoutMs = 180000L;
        private String streamPath = "/stream";
        private String ingestPath = "/ingest";
        private String knowledgeIngestPath = "/ingest/knowledge";
        private String crawlPath = "/crawl/hot-memes";
    }

    @Data
    public static class Ai {
        private int maxHistoryTokens = 3000;
        private int maxOutputTokens = 512;
        private long cacheTtlSeconds = 120L;
        private int rateLimitPerMinute = 20;
        /** 保守估算：每 token 约等于多少字符（中文场景） */
        private int charsPerTokenEstimate = 3;
        /** 是否允许 POST /ai/ingest/backfill 回填向量库 */
        private boolean backfillEnabled = true;
        /** MySQL 关键词预检索写入 hint 的条数上限（由 Assembler 使用时可覆盖） */
        private int mysqlHintLimit = 8;
    }

    /**
     * 运维接口权限（crawl / ingest 手动入口）。
     */
    @Data
    public static class Ops {
        /** 是否要求管理员（ROLE_ADMIN 或白名单） */
        private boolean requireAdmin = true;
        /**
         * 额外允许的运维用户雪花 ID（逗号分隔配置）。
         * 便于本地尚无 ROLE_ADMIN 时临时授权。
         */
        private java.util.List<Long> adminUserIds = new java.util.ArrayList<>();
    }

    private Ops ops = new Ops();

    /**
     * Firecrawl 热梗采集 → 写入 MySQL → 灌库。
     */
    @Data
    public static class Crawl {
        /** 总开关 */
        private boolean enabled = true;
        /** 定时采集开关 */
        private boolean scheduleEnabled = true;
        /** cron：默认每月 4 次（每月 1/8/15/22 日 10:00） */
        private String cron = "0 0 10 1,8,15,22 * *";
        /** 每次拉取条数（对齐 UP 投稿前 N 条视频） */
        private int limit = 5;
        /** 系统发布者用户 ID（须已存在于 user 表） */
        private long publisherUserId = 0L;
        /** 首页「实时新梗」条数 */
        private int liveLimit = 8;
        /** 实时新梗时间窗（小时） */
        private int liveHours = 48;
    }

    private Crawl crawl = new Crawl();
}
