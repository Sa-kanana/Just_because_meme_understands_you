package com.sakana.just_because_meme_understands_you.common;

/**
 * 业务异常，用于在 Service 层精确区分失败原因，由全局异常处理器统一封装为 Result 返回前端。
 * 推荐使用 {@link Result} 中的状态码常量（如 {@link Result#CODE_ERROR}、{@link Result#CODE_NOT_FOUND}）构造。
 */
public class BizException extends RuntimeException {

    private final int code;

    /** 仅消息，状态码使用默认 {@link Result#CODE_ERROR} */
    public BizException(String message) {
        this(Result.CODE_ERROR, message);
    }

    /** 指定状态码与消息 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

