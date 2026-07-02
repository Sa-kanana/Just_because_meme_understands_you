package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 发送验证码接口 data 部分返回结构
 * 对应 Apifox 中的 retryAfter
 */
@Data
public class SendCodeResponseVO {

    /**
     * 告诉前端多少秒后可以重发
     */
    private Long retryAfter;
}

