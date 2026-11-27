package com.aiinterview.video.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 视频房间 WebSocket 配置：注册信令端点、握手拦截器与跨域策略。
 *
 * <p>端点：{@code /ws/call?token=<JWT>&code=<房间码>&name=<显示名>}；
 * 该路径在 {@code SecurityConfig} 中已放行，鉴权由 {@link CallHandshakeInterceptor} 完成。</p>
 */
@Configuration
@EnableWebSocket
public class CallWebSocketConfig implements WebSocketConfigurer {

    /** 单个文本消息的最大缓冲：512KB，避免 SDP 被截断。 */
    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 512 * 1024;
    /** 会话空闲超时：10 分钟。 */
    private static final long MAX_SESSION_IDLE_TIMEOUT_MS = 600_000L;

    private final CallWebSocketHandler callWebSocketHandler;
    private final CallHandshakeInterceptor callHandshakeInterceptor;

    /**
     * 构造器注入。
     *
     * @param callWebSocketHandler       视频信令处理器
     * @param callHandshakeInterceptor   视频信令握手拦截器
     */
    public CallWebSocketConfig(CallWebSocketHandler callWebSocketHandler,
                               CallHandshakeInterceptor callHandshakeInterceptor) {
        this.callWebSocketHandler = callWebSocketHandler;
        this.callHandshakeInterceptor = callHandshakeInterceptor;
    }

    /**
     * 注册视频信令端点与握手拦截器，并放开跨域来源。
     *
     * @param registry WebSocket 注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(callWebSocketHandler, "/ws/call")
                .addInterceptors(callHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 配置 Servlet 容器级 WebSocket 参数（文本缓冲与空闲超时）。
     *
     * <p>与会话通道 {@code WebSocketConfig} 使用相同数值；两个工厂 Bean 会作用于同一个
     * Servlet 容器，配置一致因此不会互相覆盖出意外行为。</p>
     *
     * @return 容器工厂 Bean
     */
    @Bean
    public ServletServerContainerFactoryBean createCallWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxSessionIdleTimeout(MAX_SESSION_IDLE_TIMEOUT_MS);
        return container;
    }
}
