package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserRoleRequestDTO {

    /** ROLE_USER 或 ROLE_ADMIN */
    @NotBlank(message = "role 不能为空")
    private String role;
}
