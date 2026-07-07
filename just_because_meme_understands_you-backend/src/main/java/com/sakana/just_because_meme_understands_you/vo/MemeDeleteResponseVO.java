package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 删除自己发布的梗响应
 */
@Data
public class MemeDeleteResponseVO {

    /** 梗 id */
    private Long memeId;

    /** 状态（1.正常，2.审核中，3.下架/已删除） */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 删除时间，格式 yyyy-MM-dd HH:mm:ss */
    private String deletedAt;
}
