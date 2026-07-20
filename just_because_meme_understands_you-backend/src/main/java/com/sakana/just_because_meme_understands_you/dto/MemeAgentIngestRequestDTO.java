package com.sakana.just_because_meme_understands_you.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MemeAgentIngestRequestDTO {

    @Builder.Default
    private List<IngestDocument> documents = new ArrayList<>();

    @Data
    @Builder
    public static class IngestDocument {
        @JsonProperty("meme_id")
        private String memeId;

        private String title;

        private String introduction;

        @Builder.Default
        private List<String> tags = new ArrayList<>();

        private String status;

        @JsonProperty("updated_at")
        private OffsetDateTime updatedAt;

        @JsonProperty("content_hash")
        private String contentHash;
    }
}
