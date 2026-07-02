package com.sakana.just_because_meme_understands_you.dto;

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
    private String email;

    /**
     * 密码（原文）
     */
    private String password;

    /**
     * 登录方式，对应 identity_type（如 email）
     */
    private String loginType;
}

