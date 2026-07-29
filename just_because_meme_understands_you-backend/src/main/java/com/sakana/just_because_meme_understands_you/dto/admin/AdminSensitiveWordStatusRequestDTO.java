package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminSensitiveWordStatusRequestDTO {

    /** 0 禁用，1 启用 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
