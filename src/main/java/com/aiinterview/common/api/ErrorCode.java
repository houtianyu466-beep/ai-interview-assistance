package com.aiinterview.common.api;

/**
 * 统一返回码。
 *
 * <p>约定：{@code 0} 表示成功；其余为业务错误码，前三位与 HTTP 语义对齐，方便前端统一处理。</p>
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限访问"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源状态冲突"),
    RATE_LIMIT(42900, "请求过于频繁"),
    BIZ_ERROR(50000, "业务处理失败"),
    AI_ERROR(50200, "AI 服务暂时不可用"),
    SYSTEM_ERROR(50099, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
