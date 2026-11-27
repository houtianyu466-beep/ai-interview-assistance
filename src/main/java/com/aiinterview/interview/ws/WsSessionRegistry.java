package com.aiinterview.interview.ws;

import com.aiinterview.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试 WebSocket 会话注册表。
 *
 * <p>以「面试 id → 会话集合」的方式保存连接，支持同一场面试多端（多标签页）同时在线；
 * 发送消息时对单个会话加锁，避免 Tomcat 对并发写同一会话的告警。</p>
 */
@Component
public class WsSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsSessionRegistry.class);

    /** 面试 id 到会话集合的映射，键与集合本身都是线程安全的。 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话。
     *
     * @param interviewId 面试 id
     * @param session     已建立的 WebSocket 会话
     */
    public void register(Long interviewId, WebSocketSession session) {
        if (interviewId == null || session == null) {
            return;
        }
        Set<WebSocketSession> group = sessions.computeIfAbsent(interviewId, key -> ConcurrentHashMap.newKeySet());
        group.add(session);
        log.info("面试 {} 的 WebSocket 会话已注册，sessionId={}，当前连接数={}",
                interviewId, session.getId(), group.size());
    }

    /**
     * 注销会话；该面试已无连接时同时清理映射，避免内存泄漏。
     *
     * @param interviewId 面试 id
     * @param session     已关闭的 WebSocket 会话
     */
    public void unregister(Long interviewId, WebSocketSession session) {
        if (interviewId == null || session == null) {
            return;
        }
        sessions.computeIfPresent(interviewId, (key, group) -> {
            group.remove(session);
            if (group.isEmpty()) {
                log.info("面试 {} 已无在线连接，移除注册表记录", key);
                return null;
            }
            return group;
        });
    }

    /**
     * 向某场面试的所有在线会话广播消息。
     *
     * @param interviewId 面试 id
     * @param message     待发送的消息
     */
    public void sendToInterview(Long interviewId, WsMessage message) {
        if (interviewId == null || message == null) {
            return;
        }
        Set<WebSocketSession> group = sessions.get(interviewId);
        if (group == null || group.isEmpty()) {
            log.debug("面试 {} 没有在线会话，消息 {} 被丢弃", interviewId, message.type());
            return;
        }
        String payload = JsonUtils.toJson(message);
        if (payload == null) {
            return;
        }
        for (WebSocketSession session : group) {
            sendText(session, payload);
        }
    }

    /**
     * 向单个会话发送消息，用于对当前请求的精确回复。
     *
     * @param session 目标会话
     * @param message 待发送的消息
     */
    public void sendToSession(WebSocketSession session, WsMessage message) {
        if (session == null || message == null) {
            return;
        }
        String payload = JsonUtils.toJson(message);
        if (payload == null) {
            return;
        }
        sendText(session, payload);
    }

    /**
     * 关闭并清理某场面试的全部会话。
     *
     * @param interviewId 面试 id
     */
    public void closeAll(Long interviewId) {
        if (interviewId == null) {
            return;
        }
        Set<WebSocketSession> group = sessions.remove(interviewId);
        if (group == null || group.isEmpty()) {
            return;
        }
        for (WebSocketSession session : group) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                log.warn("关闭 WebSocket 会话失败，interviewId={}，sessionId={}", interviewId, session.getId(), e);
            }
        }
        log.info("面试 {} 的 {} 个 WebSocket 会话已关闭", interviewId, group.size());
    }

    /**
     * 发送文本消息：对会话同步并吞掉 IO 异常，避免单个连接故障影响其他连接。
     */
    private void sendText(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            log.debug("WebSocket 会话已关闭，跳过发送，sessionId={}", session.getId());
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.error("WebSocket 消息发送失败，sessionId={}", session.getId(), e);
        }
    }
}
