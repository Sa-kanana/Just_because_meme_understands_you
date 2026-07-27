package com.sakana.just_because_meme_understands_you.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MemeAgent POST /crawl/hot-memes 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemeAgentCrawlResponseDTO {

    @JsonProperty("query_used")
    private List<String> queryUsed = new ArrayList<>();

    private List<Candidate> candidates = new ArrayList<>();

    @JsonProperty("credits_hint")
    private String creditsHint;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private String title;
        private String introduction;
        @JsonProperty("source_url")
        private String sourceUrl;
        @JsonProperty("image_url")
        private String imageUrl;
        @JsonProperty("detail_url")
        private String detailUrl;
        private List<String> tags = new ArrayList<>();
        private Double score;
    }
}
