package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.projection;

import lombok.Data;

/**
 * 标签与梗的关联行，用于一次 JOIN 查询批量取回每个梗的标签，
 * 替代 Service 层「查关系表 + 批量查标签表」两次查询。
 *
 * @author sakana
 */
@Data
public class MemeTagBindDTO {

    /** 所属梗 id */
    private Long memeId;

    /** 标签 id */
    private Integer id;

    /** 标签名 */
    private String name;

    /** 中文注释：标签分类代码。 */
    private String category;

    /** 中文注释：该标签是否为所属梗的主分类标签。 */
    private Boolean isPrimary;

    /** 相关梗数量（数据库字段为 INT） */
    private Integer relatedQuantity;
}
