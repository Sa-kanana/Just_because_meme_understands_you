package com.sakana.just_because_meme_understands_you.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体
 * 对应 Apifox 中的 email、password、loginType
 */
@Data
public class LoginRequestDTO {

    /**
     * 对应数据库 identifier
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 密码（原文）
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 登录方式，对应 identity_type（如 email）
     */
    @NotBlank(message = "登录方式不能为空")
    private String loginType;
}
