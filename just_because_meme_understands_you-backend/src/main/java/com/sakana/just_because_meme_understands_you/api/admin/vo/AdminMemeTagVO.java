package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;

/** 管理端梗详情中的标签信息。 */
@Data
public class AdminMemeTagVO {

    /** 标签 ID。 */
    private Long id;

    /** 标签名称。 */
    private String name;

    /** 中文注释：标签所属分类代码。 */
    private String category;

    /** 中文注释：该标签是否为当前梗的主分类标签。 */
    private Boolean isPrimary;

    /** 当前标签关联的梗数量。 */
    private Integer relatedQuantity;
}
