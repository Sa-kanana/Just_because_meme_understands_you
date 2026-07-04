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

    /** OSS Bucket 访问域名 */
    private String host;

    /** 凭证过期时间戳（秒） */
    private long expire;
}
