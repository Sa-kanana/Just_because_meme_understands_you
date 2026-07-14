package com.sakana.just_because_meme_understands_you.util;

import org.springframework.util.StringUtils;

/**
 * 邮箱脱敏展示（账号设置安全区）。
 */
public final class EmailMaskUtil {

    private EmailMaskUtil() {
    }

    /**
     * 例：ab***@example.com；过短本地部分用 * 占位。
     */
    public static String mask(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        String raw = email.trim();
        int at = raw.indexOf('@');
        if (at <= 0 || at == raw.length() - 1) {
            return "***";
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
