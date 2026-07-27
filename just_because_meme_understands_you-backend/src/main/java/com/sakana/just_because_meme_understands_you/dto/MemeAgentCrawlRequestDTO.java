package com.sakana.just_because_meme_understands_you.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

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
}
