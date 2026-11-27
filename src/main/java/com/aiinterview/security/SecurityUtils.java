package com.aiinterview.security;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 中读取当前登录用户的工具类。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser;
    }

    public static Long currentUserId() {
        return currentUser().id();
    }

    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * 当前用户是否为面试官角色。
     */
    public static boolean isInterviewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INTERVIEWER".equals(a.getAuthority()));
    }

    /**
     * 校验资源归属：本人的资源或管理员可以访问。
     */
    public static void requireOwner(Long ownerId) {
        LoginUser loginUser = currentUser();
        if (!loginUser.isAdmin() && !loginUser.id().equals(ownerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问他人数据");
        }
    }
}
