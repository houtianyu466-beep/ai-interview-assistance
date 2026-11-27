package com.aiinterview.interview.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置：注册面试通道端点、握手拦截器与跨域策略。
 *
 * <p>端点：{@code /ws/interview?token=<JWT>&interviewId=<面试id>}；
 * 该路径在 {@code SecurityConfig} 中已放行，鉴权由 {@link InterviewHandshakeInterceptor} 完成。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 单个文本消息的最大缓冲：512KB，避免长回答被截断。 */
    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 512 * 1024;
    /** 会话空闲超时：10 分钟。 */
    private static final long MAX_SESSION_IDLE_TIMEOUT_MS = 600_000L;

    private final InterviewWebSocketHandler interviewWebSocketHandler;
    private final InterviewHandshakeInterceptor interviewHandshakeInterceptor;

    /**
     * 构造器注入。
     *
     * @param interviewWebSocketHandler   面试通道处理器
     * @param interviewHandshakeInterceptor 握手鉴权拦截器
     */
    public WebSocketConfig(InterviewWebSocketHandler interviewWebSocketHandler,
                           InterviewHandshakeInterceptor interviewHandshakeInterceptor) {
        this.interviewWebSocketHandler = interviewWebSocketHandler;
        this.interviewHandshakeInterceptor = interviewHandshakeInterceptor;
    }

    /**
     * 注册 WebSocket 端点与握手拦截器，并放开跨域来源。
     *
     * @param registry WebSocket 注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewWebSocketHandler, "/ws/interview")
                .addInterceptors(interviewHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 配置 Servlet 容器级 WebSocket 参数（文本缓冲与空闲超时）。
     *
     * @return 容器工厂 Bean
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxSessionIdleTimeout(MAX_SESSION_IDLE_TIMEOUT_MS);
        return container;
    }
}
