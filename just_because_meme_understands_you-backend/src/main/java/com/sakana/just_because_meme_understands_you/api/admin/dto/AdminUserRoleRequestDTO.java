package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserRoleRequestDTO {

    /** 目标角色代码：USER、ADMIN 或 SUPER_ADMIN，也接受带 ROLE_ 前缀的兼容写法。 */
    @NotBlank(message = "role 不能为空")
    private String role;
}
