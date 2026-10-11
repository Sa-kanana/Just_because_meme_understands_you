package com.sakana.just_because_meme_understands_you.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端逐条新增梗的请求参数。 */
@Data
public class AdminMemeCreateRequestDTO {

    /** 梗名，最多 50 个字符。 */
    @NotBlank(message = "梗名称不能为空")
    @Size(max = 50, message = "梗名称最多 50 个字符")
    private String name;

    /** 梗介绍，最多 255 个字符。 */
    @Size(max = 255, message = "梗介绍最多 255 个字符")
    private String introduction;

    /** 中文注释：可选的封面图片地址；不填写时使用默认封面展示。 */
    @Size(max = 500, message = "封面图地址过长")
    private String image;

    /** 关联的标签 ID 列表，可不选择标签。 */
    private List<Integer> tagIds;
}
