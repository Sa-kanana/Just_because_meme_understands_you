package com.sakana.just_because_meme_understands_you.common;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器，将各类异常统一封装为 {@link Result} 返回前端，保证接口响应格式一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（如参数校验、登录失败、注册失败等），直接返回业务码与提示。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletResponse response) {
        if (response != null) {
            if (e.getCode() == Result.CODE_UNAUTHORIZED || e.getCode() == Result.CODE_REFRESH_TOKEN_EXPIRED) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
            } else if (e.getCode() == Result.CODE_TOO_MANY_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * Bean 校验失败（如 @Valid），返回第一个字段的校验信息。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex) {
            List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
            if (!fieldErrors.isEmpty()) {
                msg = fieldErrors.get(0).getDefaultMessage();
            }
        } else if (e instanceof BindException ex) {
            List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
            if (!fieldErrors.isEmpty()) {
                msg = fieldErrors.get(0).getDefaultMessage();
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("参数校验异常: {}", msg);
        }
        return Result.fail(Result.CODE_BAD_REQUEST, msg);
    }

    /**
     * 必填请求参数缺失。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = "缺少必填参数：" + e.getParameterName();
        if (log.isDebugEnabled()) {
            log.debug("缺少参数: {}", msg);
        }
        return Result.fail(Result.CODE_ERROR, msg);
    }

    /**
     * 请求体反序列化失败（如 JSON 格式错误、类型不匹配）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        if (log.isDebugEnabled()) {
            log.debug("请求体不可读: {}", e.getMessage());
        }
        return Result.fail(Result.CODE_ERROR, "请求体格式错误，请检查入参");
    }

    /**
     * 请求参数类型不匹配（如期望数字传了字符串）。
     */
    @ExceptionHandler({TypeMismatchException.class, MethodArgumentTypeMismatchException.class})
    public Result<Void> handleTypeMismatch(Exception e) {
        String msg = "参数类型错误";
        if (e instanceof MethodArgumentTypeMismatchException ex && ex.getName() != null) {
            msg = "参数「" + ex.getName() + "」类型不正确";
        } else if (e instanceof TypeMismatchException ex && ex.getPropertyName() != null) {
            msg = "参数「" + ex.getPropertyName() + "」类型不正确";
        }
        if (log.isDebugEnabled()) {
            log.debug("参数类型不匹配: {}", e.getMessage());
        }
        return Result.fail(Result.CODE_BAD_REQUEST, msg);
    }

    /**
     * HTTP 方法不允许（如对 GET 接口发 POST）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        if (log.isDebugEnabled()) {
            log.debug("请求方法不允许: {}", e.getMethod());
        }
        return Result.fail(Result.CODE_BAD_REQUEST, "请求方法不允许");
    }

    /**
     * 非法参数或状态（如工具类抛出的 IllegalArgumentException / IllegalStateException）。
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Result<Void> handleIllegalArgument(Exception e) {
        String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "参数或状态错误";
        if (log.isWarnEnabled()) {
            log.warn("非法参数或状态: {}", msg);
        }
        return Result.fail(Result.CODE_BAD_REQUEST, msg);
    }

    /**
     * 无权限访问（如已登录但访问无权限资源）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        if (log.isDebugEnabled()) {
            log.debug("无权限: {}", e.getMessage());
        }
        return Result.fail(Result.CODE_FORBIDDEN, "无权限访问");
    }

    /**
     * 兜底：未分类异常统一返回友好提示，避免堆栈暴露给前端；详细异常记录日志便于排查。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOtherException(Exception e) {
        log.error("未捕获异常，统一返回友好提示", e);
        return Result.fail(Result.CODE_ERROR, "系统繁忙，请稍后重试");
    }
}
