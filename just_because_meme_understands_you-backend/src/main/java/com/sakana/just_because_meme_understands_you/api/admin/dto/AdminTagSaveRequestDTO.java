package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminTagSaveRequestDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名最多 50 字")
    private String name;

    /** 中文注释：标签所属分类代码，创建标签时必须选择受支持的分类。 */
    @NotBlank(message = "请选择标签分类")
    private String category;

    /** 中文注释：标签在所属分类中的展示顺序，未提供时由服务层追加到分类末尾。 */
    private Integer sortOrder;
}
