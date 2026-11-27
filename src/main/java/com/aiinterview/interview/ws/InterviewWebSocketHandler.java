package com.aiinterview.interview.ws;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.dto.AnswerResultVO;
import com.aiinterview.interview.dto.CurrentQuestionVO;
import com.aiinterview.interview.dto.SubmitAnswerRequest;
import com.aiinterview.interview.service.InterviewService;
import com.aiinterview.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 面试实时通道的消息处理器（原生 WebSocket，非 STOMP）。
 *
 * <p>客户端上行消息统一为 {@link WsMessage} JSON，按 {@code type} 分派：</p>
 * <ul>
 *     <li>{@code START}：调用 {@code interviewService.start} 并下发第一题；</li>
 *     <li>{@code ANSWER}：以 {@code content} 为回答正文、{@code data.questionId} 为题目 id、
 *         {@code thinkingSeconds} 为思考秒数（为空按 0）调用 {@code submitAnswer}；
 *         结束则下发 {@code FINISHED}，否则下发 {@code SCORE}；</li>
 *     <li>{@code SWITCH}：调用 {@code reportSwitch} 并回 {@code SWITCH_ACK}；</li>
 *     <li>{@code PING}：回 {@code PONG}；</li>
 *     <li>其他类型：回 {@code ERROR}。</li>
 * </ul>
 *
 * <p>鉴权信息由 {@link InterviewHandshakeInterceptor} 写入会话属性，此处只读取，不重复校验。</p>
 */
@Component
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InterviewWebSocketHandler.class);

    private final WsSessionRegistry wsSessionRegistry;
    private final InterviewService interviewService;

    /**
     * 构造器注入。
     *
     * @param wsSessionRegistry 会话注册表
     * @param interviewService  面试服务
     */
    public InterviewWebSocketHandler(WsSessionRegistry wsSessionRegistry, InterviewService interviewService) {
        this.wsSessionRegistry = wsSessionRegistry;
        this.interviewService = interviewService;
    }

    /**
     * 连接建立后：注册会话并推送 {@code CONNECTED}。
     *
     * @param session 新建的会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long interviewId = interviewIdOf(session);
        LoginUser loginUser = loginUserOf(session);
        if (interviewId == null || loginUser == null) {
            log.warn("WebSocket 会话缺少握手属性，关闭连接，sessionId={}", session.getId());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        wsSessionRegistry.register(interviewId, session);
        wsSessionRegistry.sendToSession(session, WsMessage.connected(interviewId));
        log.info("用户 {} 已连接面试 {} 的实时通道，sessionId={}", loginUser.id(), interviewId, session.getId());
    }

    /**
     * 处理客户端文本消息：解析、分派，并把业务异常转成 {@code ERROR} 回推。
     *
     * @param session 当前会话
     * @param message 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long interviewId = interviewIdOf(session);
        LoginUser loginUser = loginUserOf(session);
        if (interviewId == null || loginUser == null) {
            log.warn("WebSocket 会话缺少握手属性，关闭连接，sessionId={}", session.getId());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            WsMessage payload = JsonUtils.fromJson(message.getPayload(), WsMessage.class);
            if (payload == null || TextUtils.isBlank(payload.type())) {
                throw BizException.badRequest("消息格式不正确：type 不能为空");
            }
            dispatch(session, interviewId, loginUser, payload);
        } catch (BizException e) {
            log.warn("WebSocket 业务处理失败，interviewId={}，原因={}", interviewId, e.getMessage());
            wsSessionRegistry.sendToSession(session, WsMessage.error(e.getMessage()));
        } catch (Exception e) {
            log.error("WebSocket 消息处理异常，interviewId={}，sessionId={}", interviewId, session.getId(), e);
            wsSessionRegistry.sendToSession(session, WsMessage.error("服务器内部错误"));
        }
    }

    /**
     * 连接关闭后：从注册表注销会话。
     *
     * @param session 已关闭的会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long interviewId = interviewIdOf(session);
        if (interviewId != null) {
            wsSessionRegistry.unregister(interviewId, session);
        }
        log.info("WebSocket 连接关闭，interviewId={}，sessionId={}，status={}",
                interviewId, session.getId(), status);
    }

    /**
     * 按消息类型分派。
     */
    private void dispatch(WebSocketSession session, Long interviewId, LoginUser loginUser, WsMessage message) {
        String type = message.type().trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case WsMessage.TYPE_START -> handleStart(session, interviewId);
            case WsMessage.TYPE_ANSWER -> handleAnswer(session, interviewId, loginUser, message);
            case WsMessage.TYPE_SWITCH -> handleSwitch(session, interviewId, loginUser);
            case WsMessage.TYPE_PING -> wsSessionRegistry.sendToSession(session, WsMessage.pong());
            default -> throw BizException.badRequest("不支持的消息类型：" + message.type());
        }
    }

    /**
     * 处理 {@code START}：开始面试并下发第一题。
     */
    private void handleStart(WebSocketSession session, Long interviewId) {
        CurrentQuestionVO question = interviewService.start(interviewId);
        if (question == null) {
            throw new BizException("面试已开始，但未获取到题目");
        }
        wsSessionRegistry.sendToSession(session, WsMessage.question(question));
    }

    /**
     * 处理 {@code ANSWER}：提交回答，结束时下发 {@code FINISHED}，否则下发 {@code SCORE}。
     */
    private void handleAnswer(WebSocketSession session, Long interviewId, LoginUser loginUser, WsMessage message) {
        SubmitAnswerRequest request = toSubmitAnswerRequest(message);
        AnswerResultVO result = interviewService.submitAnswer(interviewId, loginUser.id(), request);
        if (result == null) {
            throw new BizException("作答结果为空，请稍后重试");
        }
        if (result.finished()) {
            wsSessionRegistry.sendToSession(session,
                    WsMessage.finished(result.reportId(), extractOverallScore(result)));
        } else {
            wsSessionRegistry.sendToSession(session, WsMessage.score(result));
        }
    }

    /**
     * 处理 {@code SWITCH}：记录切题并回 {@code SWITCH_ACK}。
     */
    private void handleSwitch(WebSocketSession session, Long interviewId, LoginUser loginUser) {
        interviewService.reportSwitch(interviewId, loginUser.id());
        wsSessionRegistry.sendToSession(session, WsMessage.switched(null));
    }

    /**
     * 由 {@link WsMessage} 构造提交答案请求：{@code content} 为回答正文（必填），
     * {@code data.questionId} 为题目 id，{@code thinkingSeconds} 为空按 0 处理。
     */
    private SubmitAnswerRequest toSubmitAnswerRequest(WsMessage message) {
        if (TextUtils.isBlank(message.content())) {
            throw BizException.badRequest("回答内容不能为空");
        }
        Long questionId = extractQuestionId(message.data());
        if (questionId == null) {
            throw BizException.badRequest("ANSWER 消息缺少 data.questionId");
        }
        int thinkingSeconds = message.thinkingSeconds() == null ? 0 : message.thinkingSeconds();
        if (thinkingSeconds < 0) {
            throw BizException.badRequest("thinkingSeconds 不能为负数");
        }
        return new SubmitAnswerRequest(questionId, message.content().trim(), thinkingSeconds);
    }

    /**
     * 从 {@code data} 中读取 {@code questionId}，兼容数字与字符串两种写法。
     */
    private Long extractQuestionId(Object data) {
        Object value = toMap(data).get("questionId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 尝试从作答结果中读取整体分；当前 {@link AnswerResultVO} 未包含该字段时返回 null，
     * 此时 {@code FINISHED} 消息只携带 reportId，由客户端再拉取报告。
     */
    private Double extractOverallScore(Object data) {
        Object value = toMap(data).get("overallScore");
        return value instanceof Number number ? number.doubleValue() : null;
    }

    /**
     * 把任意对象（通常为 Jackson 反序列化出的 Map）转成 {@code Map<String, Object>}。
     */
    private Map<String, Object> toMap(Object data) {
        if (data == null) {
            return Map.of();
        }
        if (data instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        try {
            return JsonUtils.toMap(JsonUtils.toJson(data));
        } catch (RuntimeException e) {
            log.warn("WebSocket 消息 data 解析失败：{}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 读取握手时写入的面试 id。
     */
    private Long interviewIdOf(WebSocketSession session) {
        Object value = session.getAttributes().get(InterviewHandshakeInterceptor.ATTR_INTERVIEW_ID);
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * 读取握手时写入的登录用户。
     */
    private LoginUser loginUserOf(WebSocketSession session) {
        Object value = session.getAttributes().get(InterviewHandshakeInterceptor.ATTR_LOGIN_USER);
        return value instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 安静关闭会话，异常仅记录日志。
     */
    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.warn("关闭 WebSocket 会话失败，sessionId={}", session.getId(), e);
        }
    }
}
