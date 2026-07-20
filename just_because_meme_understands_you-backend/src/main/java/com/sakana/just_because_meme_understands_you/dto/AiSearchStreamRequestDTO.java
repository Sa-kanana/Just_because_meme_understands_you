package com.sakana.just_because_meme_understands_you.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiSearchStreamRequestDTO {

    @NotBlank(message = "query 不能为空")
    @Size(max = 2000, message = "query 过长")
    private String query;

    /** 已有会话 id；空则创建新会话 */
    private String sessionId;

    /** 客户端生成的请求 id，用于幂等与追踪 */
    @Size(max = 64, message = "requestId 过长")
    private String requestId;
}
