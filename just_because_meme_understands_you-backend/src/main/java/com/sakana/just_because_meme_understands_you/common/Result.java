package com.sakana.just_because_meme_understands_you.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回结构。
 * 所有接口均使用 code + message + data，便于前端统一处理。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 成功 */
    public static final int CODE_SUCCESS = 1;
    /** 默认业务/参数错误 */
    public static final int CODE_ERROR = 0;
    /** 请求参数错误（如 400） */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未授权（如 401） */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 请求过于频繁（如 429） */
    public static final int CODE_TOO_MANY_REQUESTS = 429;
    /** 资源不存在（如 404） */
    public static final int CODE_NOT_FOUND = 404;
    /** 无权限（403） */
    public static final int CODE_FORBIDDEN = 403;
    /** refresh token 失效/过期，需重新登录 */
    public static final int CODE_REFRESH_TOKEN_EXPIRED = 1002;

    /** 默认成功提示 */
    public static final String MSG_SUCCESS = "success";

    /**
     * 业务状态码，{@link #CODE_SUCCESS} 表示成功
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 具体数据，失败时通常为 null
     */
    private T data;

    // ---------- 成功 ----------

    /** 无数据成功（如仅需 code + message） */
    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, MSG_SUCCESS, null);
    }

    /** 成功并返回数据，message 固定为 "success" */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, MSG_SUCCESS, data);
    }

    /** 成功并返回自定义 message 与 data（如发送验证码等需自定义提示） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(CODE_SUCCESS, message, data);
    }

    // ---------- 失败 ----------

    /** 失败，仅 message，code 使用默认 {@link #CODE_ERROR} */
    public static <T> Result<T> fail(String message) {
        return new Result<>(CODE_ERROR, message, null);
    }

    /** 失败，指定 code 与 message */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

}
