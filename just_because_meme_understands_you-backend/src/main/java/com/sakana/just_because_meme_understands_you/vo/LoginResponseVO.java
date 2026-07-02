package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 登录接口 data 部分返回结构
 */
@Data
public class LoginResponseVO {

    /**
     * Access Token（JWT）
     */
    private String token;

    /**
     * 用户信息
     */
    private LoginUserVO user;
}

