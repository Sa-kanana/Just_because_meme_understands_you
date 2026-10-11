package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MemeAgentKnowledgeIngestRequestDTO {

    @Builder.Default
    private List<KnowledgeDocument> documents = new ArrayList<>();

    @Data
    @Builder
    public static class KnowledgeDocument {
        @JsonProperty("doc_id")
        private String docId;

        private String title;

        private String content;

        private String category;

        @Builder.Default
        private List<String> tags = new ArrayList<>();

        private String status;

        @JsonProperty("updated_at")
        private OffsetDateTime updatedAt;

        @JsonProperty("content_hash")
        private String contentHash;

        /** 中文注释：标准化正文的 SHA-256 去重指纹。 */
        @JsonProperty("content_sha256")
        private String contentSha256;

        /** 中文注释：单调递增的向量任务版本，用于拒绝过期事件。 */
        private Long revision;
    }
}
