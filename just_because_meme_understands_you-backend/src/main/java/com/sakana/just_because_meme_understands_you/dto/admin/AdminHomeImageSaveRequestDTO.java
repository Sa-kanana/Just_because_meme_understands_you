package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminHomeImageSaveRequestDTO {

    private String title;

    @NotBlank(message = "图片地址不能为空")
    private String imgUrl;

    /** 0 无跳转，1 内部梗 ID，2 外部链接 */
    @NotNull(message = "targetType 不能为空")
    private Integer targetType;

    private String targetValue;

    private Integer sortOrder;

    /** 0 下线，1 上线；新增默认 1 */
    private Integer status;
}
