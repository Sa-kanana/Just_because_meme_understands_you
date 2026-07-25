package com.sakana.just_because_meme_understands_you.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交反馈请求。
 */
@Data
public class FeedbackSubmitRequestDTO {

    /**
     * 反馈类型：bug / suggestion / report / other
     */
    @NotBlank(message = "请选择反馈类型")
    @Size(max = 32, message = "反馈类型不合法")
    private String category;

    /**
     * 反馈正文
     */
    @NotBlank(message = "请填写反馈内容")
    @Size(min = 5, max = 2000, message = "反馈内容长度需在 5～2000 字")
    private String content;

    /**
     * 可选联系邮箱（便于回访；未填则使用账号邮箱）
     */
    @Size(max = 128, message = "联系邮箱过长")
    private String contactEmail;

    /**
     * 可选：提交时所在页面路径
     */
    @Size(max = 500, message = "页面地址过长")
    private String pageUrl;
}
