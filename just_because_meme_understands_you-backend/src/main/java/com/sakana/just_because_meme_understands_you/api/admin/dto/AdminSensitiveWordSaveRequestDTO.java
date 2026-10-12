package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminSensitiveWordSaveRequestDTO {

    @NotBlank(message = "敏感词不能为空")
    @Size(max = 64, message = "敏感词最多 64 字")
    private String word;

    @NotBlank(message = "分类不能为空")
    @Size(max = 32, message = "分类最多 32 字")
    private String category;

    /** 中文注释：风险等级，支持 NORMAL、REVIEW、BLOCK。 */
    @NotBlank(message = "level 不能为空")
    private String level;

    /** 中文注释：NORMAL 等级的命中替换文本，可为空。 */
    @Size(max = 20, message = "替换文本最多 20 字")
    private String replacement;

    /** 0 禁用，1 启用；默认 1 */
    private Integer status;
}
