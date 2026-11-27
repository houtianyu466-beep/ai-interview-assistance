package com.aiinterview.common.exception;

import com.aiinterview.common.api.ErrorCode;

/**
 * 业务异常。Service 层遇到可预期的错误（参数非法、状态不允许、资源不存在等）直接抛出，
 * 由 {@link GlobalExceptionHandler} 统一转成标准响应。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(String message) {
        this(ErrorCode.BIZ_ERROR, message);
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }
}
