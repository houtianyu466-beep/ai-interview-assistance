package com.aiinterview.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解，标注在 Controller 方法上，由 {@link OperationLogAspect} 统一记录。
 *
 * <pre>{@code
 * @OperationLog("创建面试")
 * @PostMapping
 * public Result<InterviewVO> create(...) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作描述 */
    String value();
}
