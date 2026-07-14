package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 账号安全概览，对齐 Apifox Setting.security。
 */
@Data
public class AccountSecurityVO {

    private String emailMasked;

    private Boolean emailBound;

    private Boolean passwordSet;

    /** ISO-8601 日期时间；从未改密时为空串 */
    private String lastPasswordChangeTime;

    private Boolean hasOtherSessions;
}
