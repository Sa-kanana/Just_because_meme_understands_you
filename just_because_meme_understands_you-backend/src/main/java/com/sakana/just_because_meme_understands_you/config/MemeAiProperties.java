package com.sakana.just_because_meme_understands_you.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meme.ai")
public class MemeAiProperties {

    private int maxHistoryTokens = 3000;
    private int maxOutputTokens = 512;
    private int cacheTtlSeconds = 120;
    private int rateLimitPerMinute = 20;
    /** 保守估算：每 token 约等于多少字符（中文场景） */
    private int charsPerTokenEstimate = 3;
}
