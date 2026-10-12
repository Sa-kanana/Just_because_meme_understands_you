package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

/**
 * 接口 MemeTag：标签
 */
@Data
public class MemeTagVO {

    /**
     * 唯一标识
     */
    private Integer id;

    /**
     * 标签名
     */
    private String name;

    /** 中文注释：标签所属分类代码，例如 SOURCE、FORMAT、EMOTION。 */
    private String category;

    /** 中文注释：该标签是否是当前梗的主分类标签。标签选项接口中固定为 false。 */
    private Boolean isPrimary;

    /**
     * 相关数量
     */
    private Integer relatedQuantity;
}
