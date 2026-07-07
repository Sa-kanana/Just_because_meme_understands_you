package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 发布梗时的结构化相关链接项
 */
@Data
public class MemeResourceItemDTO {

    /** 链接类型：link / article / video / image 等 */
    private String type;

    /** 外链地址（须为 http/https） */
    private String url;

    /** 展示标题 */
    private String title;

    /** 排序权重，越小越靠前 */
    private Integer sortOrder;
}
