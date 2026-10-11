package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;

/** 管理端梗详情中的标签信息。 */
@Data
public class AdminMemeTagVO {

    /** 标签 ID。 */
    private Long id;

    /** 标签名称。 */
    private String name;

    /** 当前标签关联的梗数量。 */
    private Integer relatedQuantity;
}
