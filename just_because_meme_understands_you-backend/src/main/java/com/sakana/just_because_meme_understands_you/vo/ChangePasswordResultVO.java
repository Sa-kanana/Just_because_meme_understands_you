package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 登录态改密结果。
 */
@Data
public class ChangePasswordResultVO {

    /** 改密成功后一律要求重新登录（全会话吊销 + 令牌版本递增） */
    private Boolean requireReLogin;
}
