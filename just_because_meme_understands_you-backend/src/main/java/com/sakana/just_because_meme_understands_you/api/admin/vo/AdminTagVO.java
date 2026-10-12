package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;

@Data
public class AdminTagVO {

    private Integer id;
    private String name;
    /** 中文注释：标签所属分类代码。 */
    private String category;
    /** 中文注释：标签在所属分类中的展示顺序。 */
    private Integer sortOrder;
    private String relatedQuantity;
}
