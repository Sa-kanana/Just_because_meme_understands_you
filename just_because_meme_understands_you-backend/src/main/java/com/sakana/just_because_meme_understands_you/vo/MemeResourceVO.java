package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 接口 MemeResource：相关链接 / 媒体资源
 */
@Data
public class MemeResourceVO {

    /** 唯一标识 */
    private Integer id;

    /** 资源类型：link / media / video / article / image */
    private String type;

    /** 展示标题 */
    private String title;

    /** 资源地址 */
    private String url;

    /** 排序权重 */
    private Integer sortOrder;
}
