package com.sakana.just_because_meme_understands_you.common.exception;

import com.sakana.just_because_meme_understands_you.common.response.Result;

/**
 * 业务异常，用于在 Service 层精确区分失败原因，由全局异常处理器统一封装为 Result 返回前端。
 * 推荐使用 {@link Result} 中的状态码常量（如 {@link Result#CODE_ERROR}、{@link Result#CODE_NOT_FOUND}）构造。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /** 仅消息，状态码使用默认 {@link Result#CODE_ERROR} */
    public BizException(String message) {
        this(Result.CODE_ERROR, message);
    }

    /** 保留默认业务码并关联底层异常，便于日志追踪根因。 */
    public BizException(String message, Throwable cause) {
        this(Result.CODE_ERROR, message, cause);
    }

    /** 指定状态码与消息 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 指定状态码、消息与底层异常。 */
    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

