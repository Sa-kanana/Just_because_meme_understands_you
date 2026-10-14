package com.sakana.just_because_meme_understands_you.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 中文注释：作者编辑梗时提交的可修改内容，不包含作者、状态或互动计数。 */
@Data
public class MemeEditRequestDTO {

    /** 中文注释：梗名称，去除首尾空格后最多50个字符。 */
    @NotBlank
    @Size(max = 50)
    private String name;

    /** 中文注释：梗介绍，修改时必填，最多255个字符。 */
    @NotBlank
    @Size(max = 255)
    private String introduction;

    /** 中文注释：可选的梗封面图 OSS 地址；未填写时使用默认封面展示。 */
    @Size(max = 500)
    private String image;

    /** 中文注释：代表梗主要分类的标签 ID。 */
    private Integer primaryTagId;

    /** 中文注释：梗关联的普通标签 ID，选填，最多7个。 */
    @Size(max = 7)
    private List<Integer> tagIds;

    /** 中文注释：梗相关图片、视频或 GIF 的地址列表，最多6个。 */
    @Size(max = 6)
    private List<String> resourceUrls;

    /** 中文注释：梗相关外链列表，最多10条。 */
    @Size(max = 10)
    private List<MemeResourceItemDTO> resources;
}
