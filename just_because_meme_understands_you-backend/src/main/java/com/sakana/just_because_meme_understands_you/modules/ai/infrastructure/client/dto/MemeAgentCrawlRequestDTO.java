package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 调用 MemeAgent POST /crawl/hot-memes 的请求体。
 */
@Data
@Builder
public class MemeAgentCrawlRequestDTO {

    private String query;

    @Builder.Default
    private Integer limit = 8;

    @JsonProperty("include_markdown")
    @Builder.Default
    private Boolean includeMarkdown = true;

    /** 已入库的 B 站视频 ID，供 MemeAgent 在发现阶段跳过重复视频。 */
    @JsonProperty("excluded_bilibili_video_ids")
    @Builder.Default
    private List<String> excludedBilibiliVideoIds = List.of();
}
