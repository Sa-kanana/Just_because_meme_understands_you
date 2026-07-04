package com.sakana.just_because_meme_understands_you.dto;

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
    private Integer memeId;

    /** 标签 id */
    private Integer id;

    /** 标签名 */
    private String name;

    /** 相关梗数量（DB 中为 varchar，保持原样回填到实体） */
    private String relatedQuantity;
}
