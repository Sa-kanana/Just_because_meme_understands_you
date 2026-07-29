package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminHomeImageStatusRequestDTO {

    /** 0 下线，1 上线 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
