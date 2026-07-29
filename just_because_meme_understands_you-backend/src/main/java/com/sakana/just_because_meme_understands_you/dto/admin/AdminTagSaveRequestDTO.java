package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminTagSaveRequestDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名最多 50 字")
    private String name;
}
