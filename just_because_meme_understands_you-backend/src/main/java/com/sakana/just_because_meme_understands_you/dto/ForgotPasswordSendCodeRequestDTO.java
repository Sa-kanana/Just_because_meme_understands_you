package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 请求重置（发邮件）接口请求体
 */
@Data
public class ForgotPasswordSendCodeRequestDTO {

    /**
     * 用户邮箱
     */
    private String email;
}
