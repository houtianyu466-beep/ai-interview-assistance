package com.aiinterview.common.log;

import com.aiinterview.security.LoginUser;
import com.aiinterview.system.entity.SysOperationLog;
import com.aiinterview.system.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志切面：记录「谁、在什么接口、做了什么、耗时多久、是否成功」。
 *
 * <p>日志写入失败不会影响主流程（catch 后只打印 warn）。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final SysOperationLogMapper operationLogMapper;

    public OperationLogAspect(SysOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            success = false;
            throw throwable;
        } finally {
            persist(joinPoint, operationLog, success, (int) (System.currentTimeMillis() - start));
        }
    }

    private void persist(ProceedingJoinPoint joinPoint, OperationLog annotation, boolean success, int costMs) {
        try {
            SysOperationLog entity = new SysOperationLog();
            entity.setAction(annotation.value());
            entity.setMethod(((MethodSignature) joinPoint.getSignature()).getMethod().getName());
            entity.setCostMs(costMs);
            entity.setSuccess(success);

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entity.setPath(request.getMethod() + " " + request.getRequestURI());
                entity.setIp(resolveIp(request));
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
                entity.setUserId(loginUser.id());
                entity.setUsername(loginUser.username());
            }
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入操作日志失败 action={}", annotation.value(), e);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
