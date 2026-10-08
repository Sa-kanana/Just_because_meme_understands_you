package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminMemeStatusRequestDTO {

    @NotBlank(message = "梗状态不能为空")
    @Pattern(regexp = "APPROVED|REJECTED|OFFLINE|ONLINE", message = "梗状态不受支持")
    private String status;
}
