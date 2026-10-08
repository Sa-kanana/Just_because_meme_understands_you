package com.sakana.just_because_meme_understands_you.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemeReportRequestDTO {
    @NotBlank(message = "请选择举报原因")
    private String reason;
    @Size(max = 500, message = "补充说明不能超过 500 字")
    private String detail;
}
