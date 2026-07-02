package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 注册接口 data 部分返回结构
 * 对应 Apifox 中的 userId、email、nickname、createdAt
 */
@Data
public class RegisterResponseVO {

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 创建时间，使用 ISO-8601 字符串表示
     */
    private String createdAt;
}

