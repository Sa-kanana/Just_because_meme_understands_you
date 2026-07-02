package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 服务层使用的令牌颁发结果：
 * accessToken 返回给前端，refreshToken 由控制层写入 HttpOnly Cookie。
 */
@Data
public class AuthTokenBundleVO {

    private String accessToken;

    private String refreshToken;

    private LoginUserVO user;
}
