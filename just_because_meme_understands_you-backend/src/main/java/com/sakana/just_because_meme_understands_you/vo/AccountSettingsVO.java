package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 账号设置页聚合数据，对齐 Apifox Setting。
 */
@Data
public class AccountSettingsVO {

    private AccountProfileVO profile;

    private AccountSecurityVO security;
}
