package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 账号设置页资料区，对齐 Apifox Setting.profile / Author。
 */
@Data
public class AccountProfileVO {

    private String userId;

    private String nickname;

    private String avatar;

    private String signature;

    private Integer gender;

    private String birthday;
}
