package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** MemeAgent 知识入库失败响应；只映射经过服务端净化的错误摘要和片段进度。 */
@Data
public class MemeAgentKnowledgeIngestErrorDTO {

    /** 中文注释：FastAPI 错误响应 detail 对象。 */
    private Detail detail;

    @Data
    public static class Detail {

        /** 中文注释：稳定的知识向量错误代码。 */
        private String code;

        /** 中文注释：已经提交到 PostgreSQL 的向量片段数量。 */
        @JsonProperty("completed_chunks")
        private Integer completedChunks;

        /** 中文注释：当前文档切块总数。 */
        @JsonProperty("total_chunks")
        private Integer totalChunks;

        /** 中文注释：失败处理阶段标识。 */
        private String stage;

        /** 中文注释：不包含原始正文或供应商响应的安全错误摘要。 */
        private String message;
    }
}
