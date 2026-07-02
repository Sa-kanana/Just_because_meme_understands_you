package com.sakana.just_because_meme_understands_you.vo;

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

    /**
     * 相关数量
     */
    private Integer relatedQuantity;
}
