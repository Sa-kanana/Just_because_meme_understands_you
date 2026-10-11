package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 中文注释：管理员处理作者梗修改稿时提交的审核决定和说明。 */
@Data
public class AdminMemeEditReviewRequestDTO {

    /** 中文注释：审核决定，只允许 APPROVED 或 REJECTED。 */
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED")
    private String status;

    /** 中文注释：驳回时必填的审核说明，最多500个字符。 */
    @Size(max = 500)
    private String note;
}
