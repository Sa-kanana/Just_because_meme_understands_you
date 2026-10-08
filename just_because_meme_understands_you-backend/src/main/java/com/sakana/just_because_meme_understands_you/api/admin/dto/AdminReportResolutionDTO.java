package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminReportResolutionDTO {
    @NotBlank(message = "请选择处理结果")
    private String result;
    @Size(max = 500, message = "处理备注不能超过 500 字")
    private String note;
}
