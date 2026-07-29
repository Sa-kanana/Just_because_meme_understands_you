package com.sakana.just_because_meme_understands_you.dto;

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
    }
}
