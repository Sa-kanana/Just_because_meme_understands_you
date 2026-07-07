package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 彻底删除梗响应
 */
@Data
public class MemePurgeResponseVO {

    /** 梗 id */
    private Long memeId;

    /** 状态（4=已彻底删除） */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 彻底删除时间，格式 yyyy-MM-dd HH:mm:ss */
    private String purgedAt;
}

