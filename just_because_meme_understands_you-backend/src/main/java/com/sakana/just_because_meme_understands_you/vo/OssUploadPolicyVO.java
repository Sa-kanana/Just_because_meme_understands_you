package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * OSS 前端直传凭证
 *
 * @author sakana
 */
@Data
public class OssUploadPolicyVO {

    /** OSS AccessKeyId */
    private String accessKeyId;

    /** base64 编码的 policy，前端 PostObject 时原样回传 */
    private String policy;

    /** 基于 policy 与 AccessKeySecret 计算的签名 */
    private String signature;

    /** 允许上传的目录前缀，前端拼接文件名作为 object key */
    private String dir;

    /** PostObject 上传域名（始终直连 OSS） */
    private String host;

    /** 读图访问域名（开发 OSS / 生产 CDN），前端上传成功后用此域名拼接 objectKey */
    private String publicBaseUrl;

    /** 凭证过期时间戳（秒） */
    private long expire;
}
