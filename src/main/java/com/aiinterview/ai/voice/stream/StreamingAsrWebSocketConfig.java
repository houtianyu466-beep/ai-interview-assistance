package com.aiinterview.ai.voice.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 流式转写 WebSocket 配置：注册 {@code /ws/asr} 端点。
 *
 * <p>该路径在 {@code SecurityConfig} 中放行，鉴权由
 * {@link StreamingAsrHandshakeInterceptor} 完成。容器级缓冲与空闲超时统一由
 * {@code CallWebSocketConfig} 中的 {@code ServletServerContainerFactoryBean} 设置，
 * 这里不再重复声明，避免同一容器出现多个工厂 Bean 导致配置不确定。</p>
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "app.voice.stream.enabled", havingValue = "true", matchIfMissing = true)
public class StreamingAsrWebSocketConfig implements WebSocketConfigurer {

    private final StreamingAsrWebSocketHandler handler;
    private final StreamingAsrHandshakeInterceptor interceptor;

    /**
     * 构造器注入。
     *
     * @param handler     流式转写处理器
     * @param interceptor 握手拦截器
     */
    public StreamingAsrWebSocketConfig(StreamingAsrWebSocketHandler handler,
                                       StreamingAsrHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    /**
     * 注册端点。
     *
     * @param registry WebSocket 注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/asr")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }
}
