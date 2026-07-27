package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 注册接口请求体
 * 对应 Apifox 中的 email、password、confirmPassword、verificationCode、nickname
 */
@Data
public class RegisterRequestDTO {

    /**
     * 邮箱
     */
    private String email;

    /**
     * 密码（原文）
     */
    private String password;

    /**
     * 确认密码（仅用于一致性校验，不入库）
     */
    private String confirmPassword;

    /**
     * 邮箱收到的 6 位验证码
     */
    private String verificationCode;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 图形验证码会话 id（GET /captcha）
     */
    private String captchaId;

    /**
     * 用户输入的图形验证码
     */
    private String captchaCode;
}

