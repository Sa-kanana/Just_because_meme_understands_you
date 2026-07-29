package com.sakana.just_because_meme_understands_you.dto.admin;

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

    /** 1 替换，2 人工审核，3 直接拒绝 */
    @NotNull(message = "actionType 不能为空")
    private Integer actionType;

    /** 0 禁用，1 启用；默认 1 */
    private Integer status;
}
