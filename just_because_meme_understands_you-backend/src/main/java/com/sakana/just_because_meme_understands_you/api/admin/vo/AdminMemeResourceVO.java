package com.sakana.just_because_meme_understands_you.api.admin.vo;

import lombok.Data;

/** 管理端梗详情中的关联资源信息。 */
@Data
public class AdminMemeResourceVO {

    /** 资源 ID。 */
    private Long id;

    /** 资源类型：link、media、video、article 或 image。 */
    private String type;

    /** 资源展示标题。 */
    private String title;

    /** 可访问的资源地址。 */
    private String url;

    /** 资源展示排序值。 */
    private Integer sortOrder;

    /** 资源状态：1 正常、0 下架、2 已归档。 */
    private Integer status;
}
