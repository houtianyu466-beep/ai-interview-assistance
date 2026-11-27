package com.aiinterview.interview.ws;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.interview.service.InterviewService;
import com.aiinterview.security.JwtTokenProvider;
import com.aiinterview.security.LoginUser;
import com.aiinterview.security.TokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 面试 WebSocket 握手拦截器：在 HTTP Upgrade 阶段完成令牌鉴权与面试归属校验。
 *
 * <p>客户端连接地址形如：{@code ws://host/ws/interview?token=<JWT>&interviewId=<面试id>}。
 * 校验失败时直接写入 401/403 状态码并拒绝握手，连接不会建立。</p>
 */
@Component
public class InterviewHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InterviewHandshakeInterceptor.class);

    /** 会话属性键：登录用户。 */
    public static final String ATTR_LOGIN_USER = "loginUser";
    /** 会话属性键：面试 id。 */
    public static final String ATTR_INTERVIEW_ID = "interviewId";

    private final JwtTokenProvider jwtTokenProvider;
    private final InterviewService interviewService;

    /**
     * 构造器注入。
     *
     * @param jwtTokenProvider JWT 解析器
     * @param interviewService 面试服务，用于校验面试归属
     */
    public InterviewHandshakeInterceptor(JwtTokenProvider jwtTokenProvider, InterviewService interviewService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.interviewService = interviewService;
    }

    /**
     * 握手前校验：query 参数 → 令牌 → 面试归属。
     *
     * @param request    HTTP 请求
     * @param response   HTTP 响应，鉴权失败时设置状态码
     * @param wsHandler  WebSocket 处理器
     * @param attributes 会话属性，校验通过后写入 {@code loginUser} 与 {@code interviewId}
     * @return 校验通过返回 true，否则 false
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token;
        String rawInterviewId;
        try {
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams();
            token = params.getFirst("token");
            rawInterviewId = params.getFirst("interviewId");
        } catch (RuntimeException e) {
            log.warn("WebSocket 握手参数解析失败，uri={}", request.getURI(), e);
            return reject(response, HttpStatus.UNAUTHORIZED, "握手参数解析失败");
        }

        if (token == null || token.isBlank() || rawInterviewId == null || rawInterviewId.isBlank()) {
            log.warn("WebSocket 握手缺少 token 或 interviewId，uri={}", request.getURI());
            return reject(response, HttpStatus.UNAUTHORIZED, "缺少 token 或 interviewId 参数");
        }

        Long interviewId;
        try {
            interviewId = Long.valueOf(rawInterviewId.trim());
        } catch (NumberFormatException e) {
            log.warn("WebSocket 握手 interviewId 非法，interviewId={}", rawInterviewId);
            return reject(response, HttpStatus.BAD_REQUEST, "interviewId 必须为数字");
        }

        LoginUser loginUser;
        try {
            TokenPayload payload = jwtTokenProvider.parseToken(token);
            if (payload == null || payload.userId() == null) {
                log.warn("WebSocket 握手令牌载荷为空，interviewId={}", interviewId);
                return reject(response, HttpStatus.UNAUTHORIZED, "访问令牌无效");
            }
            loginUser = new LoginUser(payload.userId(), payload.username(), payload.role());
            // 本人或管理员才可进入该面试通道；返回值无需使用，仅做归属校验
            interviewService.requireOwnedInterview(interviewId, loginUser.id());
        } catch (BizException e) {
            HttpStatus status = e.getErrorCode() == ErrorCode.FORBIDDEN
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
            log.warn("WebSocket 握手鉴权失败，interviewId={}，原因={}", interviewId, e.getMessage());
            return reject(response, status, e.getMessage());
        } catch (RuntimeException e) {
            log.error("WebSocket 握手鉴权异常，interviewId={}", interviewId, e);
            return reject(response, HttpStatus.UNAUTHORIZED, "鉴权失败，请重新登录");
        }

        attributes.put(ATTR_LOGIN_USER, loginUser);
        attributes.put(ATTR_INTERVIEW_ID, interviewId);
        log.info("WebSocket 握手通过，userId={}，interviewId={}", loginUser.id(), interviewId);
        return true;
    }

    /**
     * 握手完成后回调，无需处理。
     *
     * @param request   HTTP 请求
     * @param response  HTTP 响应
     * @param wsHandler WebSocket 处理器
     * @param exception 握手异常，可为空
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理：鉴权结果已在 beforeHandshake 中写回
    }

    /**
     * 拒绝握手：写入 HTTP 状态码并返回 false。
     *
     * @param response HTTP 响应
     * @param status   状态码
     * @param reason   失败原因，仅用于日志/调试
     * @return 恒为 false
     */
    private boolean reject(ServerHttpResponse response, HttpStatus status, String reason) {
        response.setStatusCode(status);
        log.debug("拒绝 WebSocket 握手，status={}，reason={}", status.value(), reason);
        return false;
    }
}
