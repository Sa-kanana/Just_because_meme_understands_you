package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserStatusRequestDTO {

    /** 0 禁用，1 正常 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
