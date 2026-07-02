package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 验证验证码接口响应：返回重置令牌，供第三步提交新密码使用
 */
@Data
public class VerifyCodeResponseVO {

    /**
     * 重置令牌，有效期 10 分钟，前端在“执行重置”时携带
     */
    private String token;
}
