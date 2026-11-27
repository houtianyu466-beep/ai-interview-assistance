package com.aiinterview.security;

import com.aiinterview.infrastructure.cache.CacheStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>执行位置：Spring Security 过滤器链中、{@code UsernamePasswordAuthenticationFilter} 之前。</p>
 * <p>行为约定：即使令牌非法也不直接抛异常，而是清空上下文并放行，
 * 由 {@link RestAuthenticationEntryPoint} 对需要登录的接口统一返回 401，
 * 这样公开接口（登录、健康检查、Swagger）不受影响。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** 登出令牌黑名单前缀 */
    public static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CacheStore cacheStore;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CacheStore cacheStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cacheStore = cacheStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                TokenPayload payload = jwtTokenProvider.parseToken(token);
                if (cacheStore.exists(BLACKLIST_PREFIX + payload.jti())) {
                    log.debug("令牌已被登出 jti={}", payload.jti());
                } else {
                    LoginUser loginUser = new LoginUser(payload.userId(), payload.username(), payload.role());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + payload.role())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                log.debug("令牌校验失败 uri={} reason={}", request.getRequestURI(), e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
