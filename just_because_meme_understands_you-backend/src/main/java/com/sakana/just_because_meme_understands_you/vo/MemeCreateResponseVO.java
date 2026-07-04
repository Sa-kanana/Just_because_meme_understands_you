package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 发布梗响应
 *
 * @author sakana
 */
@Data
public class MemeCreateResponseVO {

    /** 新建的梗 id */
    private Integer memeId;

    /** 状态（1.正常，2.审核中，3.下架） */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;
}
