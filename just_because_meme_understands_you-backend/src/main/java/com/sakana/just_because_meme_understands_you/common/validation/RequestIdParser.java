package com.sakana.just_because_meme_understands_you.common.validation;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import org.springframework.util.StringUtils;

/** 可复用的请求标识解析与校验。 */
public final class RequestIdParser {

    private RequestIdParser() {
    }

    /** 解析必须为正数的路径 id。 */
    public static Long parseLongId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不能为空");
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 格式错误");
        }
    }

    /** 解析整数标识但保留其原有正负值语义。 */
    public static Long parseLong(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不能为空");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 格式错误");
        }
    }
}
