package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 发送注册验证码接口请求体
 * 对应 Apifox 中的 email
 */
@Data
public class SendCodeRequestDTO {

    /**
     * 用户提供的邮箱
     */
    private String email;

    /**
     * 图形验证码会话 id（GET /captcha）
     */
    private String captchaId;

    /**
     * 用户输入的图形验证码
     */
    private String captchaCode;
}

