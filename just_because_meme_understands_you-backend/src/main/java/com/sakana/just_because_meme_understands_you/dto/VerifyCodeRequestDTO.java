package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 验证验证码接口请求体
 */
@Data
public class VerifyCodeRequestDTO {

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 邮箱收到的验证码
     */
    private String code;
}
