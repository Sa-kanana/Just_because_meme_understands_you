package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserPatchRequestDTO {

    private Integer status;

    @Pattern(regexp = "(?i)(ROLE_)?(USER|ADMIN|SUPER_ADMIN)", message = "用户角色不受支持")
    private String role;
}
