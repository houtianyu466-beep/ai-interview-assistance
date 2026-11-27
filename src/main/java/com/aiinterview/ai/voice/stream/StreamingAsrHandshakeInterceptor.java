package com.aiinterview.ai.voice.stream;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
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
 * 流式转写 WebSocket 握手拦截器：校验 JWT 与语言参数。
 *
 * <p>连接地址：{@code ws://host/ws/asr?token=<JWT>&language=zh}。
 * 浏览器 WebSocket API 不能自定义请求头，因此令牌放在 query 参数，
 * 生产环境务必使用 {@code wss://}。</p>
 */
@Component
public class StreamingAsrHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StreamingAsrHandshakeInterceptor.class);

    /** 会话属性键：登录用户。 */
    public static final String ATTR_LOGIN_USER = "loginUser";
    /** 会话属性键：识别语言。 */
    public static final String ATTR_LANGUAGE = "language";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 构造器注入。
     *
     * @param jwtTokenProvider JWT 解析器
     */
    public StreamingAsrHandshakeInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 握手前校验令牌。
     *
     * @param request    HTTP 请求
     * @param response   HTTP 响应
     * @param wsHandler  WebSocket 处理器
     * @param attributes 会话属性
     * @return 校验通过返回 {@code true}
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token;
        String language;
        try {
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams();
            token = params.getFirst("token");
            language = params.getFirst("language");
        } catch (RuntimeException e) {
            log.warn("流式转写握手参数解析失败，uri={}", request.getURI(), e);
            return reject(response, "握手参数解析失败");
        }
        if (TextUtils.isBlank(token)) {
            log.warn("流式转写握手缺少 token，uri={}", request.getURI());
            return reject(response, "缺少 token 参数");
        }
        try {
            TokenPayload payload = jwtTokenProvider.parseToken(token);
            if (payload == null || payload.userId() == null) {
                return reject(response, "访问令牌无效");
            }
            LoginUser loginUser = new LoginUser(payload.userId(), payload.username(), payload.role());
            attributes.put(ATTR_LOGIN_USER, loginUser);
            attributes.put(ATTR_LANGUAGE, TextUtils.isBlank(language) ? "zh" : language.trim());
            log.info("流式转写握手通过 userId={} language={}", loginUser.id(), attributes.get(ATTR_LANGUAGE));
            return true;
        } catch (BizException e) {
            log.warn("流式转写握手鉴权失败 reason={}", e.getMessage());
            return reject(response, e.getMessage());
        } catch (RuntimeException e) {
            log.error("流式转写握手鉴权异常", e);
            return reject(response, "鉴权失败，请重新登录");
        }
    }

    /**
     * 握手完成后回调，无需处理。
     *
     * @param request   HTTP 请求
     * @param response  HTTP 响应
     * @param wsHandler WebSocket 处理器
     * @param exception 握手异常
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 鉴权结果已在 beforeHandshake 中写回
    }

    /**
     * 拒绝握手。
     *
     * @param response HTTP 响应
     * @param reason   原因
     * @return 恒为 {@code false}
     */
    private boolean reject(ServerHttpResponse response, String reason) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        log.debug("拒绝流式转写握手 reason={}", reason);
        return false;
    }
}
