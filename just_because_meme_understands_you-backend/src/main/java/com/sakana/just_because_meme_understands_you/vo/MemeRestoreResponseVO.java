package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 恢复下架梗响应
 */
@Data
public class MemeRestoreResponseVO {

    /** 梗 id */
    private Long memeId;

    /** 恢复后状态（通常为 2=审核中） */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 恢复时间，格式 yyyy-MM-dd HH:mm:ss */
    private String restoredAt;
}

