package com.sakana.just_because_meme_understands_you.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 MemeAgent FastAPI /stream 请求体对齐。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemeAgentStreamRequestDTO {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("session_id")
    private String sessionId;

    private String query;

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @Builder.Default
    private BusinessContext context = BusinessContext.builder().build();

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @Data
    @Builder
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Data
    @Builder
    public static class BusinessContext {
        @JsonProperty("user_id")
        private String userId;

        @Builder.Default
        private String locale = "zh-CN";

        @JsonProperty("hint_meme_ids")
        @Builder.Default
        private List<String> hintMemeIds = new ArrayList<>();

        @Builder.Default
        private Map<String, Object> extra = new HashMap<>();
    }
}
