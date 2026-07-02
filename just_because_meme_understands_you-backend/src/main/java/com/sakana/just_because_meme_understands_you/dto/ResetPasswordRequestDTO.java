package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 执行重置接口请求体（Apifox：/password/reset/confirm）
 */
@Data
public class ResetPasswordRequestDTO {

    /**
     * 第二步返回的重置令牌
     */
    private String token;

    /**
     * 新密码（明文，后端 BCrypt 加密后入库）
     */
    private String newPassword;
}
