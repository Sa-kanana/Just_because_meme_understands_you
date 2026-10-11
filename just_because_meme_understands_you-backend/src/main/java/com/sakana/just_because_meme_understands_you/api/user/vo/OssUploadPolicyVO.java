package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

/** 中文注释：包含文件资产编号和服务端指定对象键的 OSS 直传票据。 */
@Data
public class OssUploadPolicyVO {

    /** 中文注释：已在数据库登记的临时文件资产ID。 */
    private Long assetId;

    /** 中文注释：服务端生成且 PostObject policy 精确绑定的 OSS 对象键。 */
    private String objectKey;

    /** 中文注释：客户端用于 OSS PostObject 签名校验的 AccessKey ID。 */
    private String accessKeyId;

    /** 中文注释：Base64 编码的 PostObject policy，客户端上传时原样提交。 */
    private String policy;

    /** 中文注释：基于 policy 与 AccessKey Secret 计算的 OSS 签名。 */
    private String signature;

    /** 中文注释：PostObject 上传域名。 */
    private String host;

    /** 中文注释：公开访问域名，上传成功后用于拼接访问 URL。 */
    private String publicBaseUrl;

    /** 中文注释：票据过期时间戳，单位为秒。 */
    private long expire;
}
