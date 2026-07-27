package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 图形验证码签发响应。
 */
@Data
public class CaptchaResponseVO {

    /** 验证码会话 id，提交登录/注册时回传 */
    private String captchaId;

    /** PNG 的 data URL（含 data:image/png;base64, 前缀） */
    private String imageBase64;

    /** 有效期（秒） */
    private long expireSeconds;
}
