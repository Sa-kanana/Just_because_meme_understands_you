package com.sakana.just_because_meme_understands_you.util;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import org.springframework.util.StringUtils;

/**
 * 密码强度校验，注册与重置密码时统一执行。
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "密码不能为空");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BizException(Result.CODE_BAD_REQUEST, "密码长度需在 8～72 个字符之间");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : password.toCharArray()) {
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BizException(Result.CODE_BAD_REQUEST, "密码需同时包含字母和数字");
        }
    }
}
