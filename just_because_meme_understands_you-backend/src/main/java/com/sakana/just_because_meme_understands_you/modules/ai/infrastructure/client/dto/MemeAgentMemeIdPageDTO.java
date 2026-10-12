package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** MemeAgent pgvector 梗 ID 游标分页响应。 */
@Data
public class MemeAgentMemeIdPageDTO {

    /** 中文注释：当前页在向量库中已建立索引的梗 ID，不包含梗正文或向量。 */
    @JsonProperty("meme_ids")
    private List<String> memeIds;

    /** 中文注释：下一页游标；为 null 时表示当前页已到末尾。 */
    @JsonProperty("next_cursor")
    private String nextCursor;
}
