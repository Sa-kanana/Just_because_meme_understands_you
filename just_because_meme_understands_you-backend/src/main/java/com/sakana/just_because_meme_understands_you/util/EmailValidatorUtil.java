package com.sakana.just_because_meme_understands_you.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 邮箱校验工具，采用宽松但符合大部分邮箱规则的校验策略，
 * 避免过于严格的正则导致合法邮箱被误判。
 */
public final class EmailValidatorUtil {

    private EmailValidatorUtil() {
    }

    /**
     * 企业常用邮箱校验正则（兼顾覆盖率与可维护性）：
     * - 允许本地部分包含 RFC 常见字符：字母、数字以及 !#$%&'*+/=?^_`{|}~.-
     * - 域名部分允许多级子域：字母、数字和连字符，中间以点分隔，结尾为 2~63 位字母
     *
     * 说明：不要追求 100% RFC 覆盖，而是兼容主流邮箱（QQ、163、Gmail、Outlook、企业邮箱等）
     * 同时拦截明显非法输入。
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@" +                // local-part
            "[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,63}$"  // domain
    );

    public static boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        String trimmed = email.trim();
        if (trimmed.length() > 254) {
            return false;
        }

        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return false;
        }

        // 再做一次长度级别校验，保证 local-part 不超过 64 字节（企业常规约束）
        int atIndex = trimmed.indexOf('@');
        String localPart = trimmed.substring(0, atIndex);
        return localPart.length() <= 64;
    }
}

