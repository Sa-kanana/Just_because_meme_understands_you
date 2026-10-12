package com.sakana.just_because_meme_understands_you.modules.oss.domain;

/** 中文注释：文件资产在直传、业务绑定和延迟删除过程中的状态。 */
public enum FileAssetStatus {

    /** 中文注释：已签发上传票据，尚未确认并绑定业务的临时资产。 */
    TEMP,
    /** 中文注释：至少被一个业务字段引用的可用资产。 */
    ACTIVE,
    /** 中文注释：已无业务引用，等待缓冲期结束后删除的资产。 */
    PENDING_DELETE,
    /** 中文注释：Worker 已取得删除租约，正在调用 OSS 删除对象。 */
    DELETING,
    /** 中文注释：OSS 对象已删除，资产记录保留用于审计。 */
    DELETED
}
