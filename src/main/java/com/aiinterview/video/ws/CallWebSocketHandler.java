package com.aiinterview.video.ws;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.dto.CurrentQuestionVO;
import com.aiinterview.interview.service.InterviewService;
import com.aiinterview.security.LoginUser;
import com.aiinterview.video.entity.VideoRoom;
import com.aiinterview.video.ws.CallSessionRegistry.CallRoom;
import com.aiinterview.video.ws.CallSessionRegistry.JoinResult;
import com.aiinterview.video.ws.CallSessionRegistry.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视频房间信令处理器（原生 WebSocket，非 STOMP）。
 *
 * <p>媒体流由前端 WebRTC 直连，本处理器只做房间成员管理与信令转发：</p>
 * <ul>
 *     <li>连接建立：加入房间 → 回 {@code ROOM_STATE} → 向其他人广播 {@code PEER_JOINED}；
 *         AI 模式还会自动开始练习并下发 {@code AI_SAY}；</li>
 *     <li>{@code OFFER}/{@code ANSWER}/{@code ICE}：附加发送者身份后转发给房间内其他 peer；</li>
 *     <li>{@code CHAT}：广播文字消息；{@code HANGUP}：广播 {@code PEER_LEFT} 并关闭连接；</li>
 *     <li>{@code PING} → {@code PONG}；{@code AI_NEXT} 仅 AI 模式房主可用；</li>
 *     <li>未知类型与所有异常统一回 {@code ERROR}，不主动断开（HANGUP 除外）。</li>
 * </ul>
 *
 * <p>鉴权信息由 {@link CallHandshakeInterceptor} 写入会话属性，此处只读取，不重复校验。</p>
 */
@Component
public class CallWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CallWebSocketHandler.class);

    private final CallSessionRegistry callSessionRegistry;
    private final InterviewService interviewService;

    /**
     * 构造器注入。
     *
     * @param callSessionRegistry 视频房间会话注册表
     * @param interviewService    面试服务，AI 模式下用于开始练习与取题
     */
    public CallWebSocketHandler(CallSessionRegistry callSessionRegistry, InterviewService interviewService) {
        this.callSessionRegistry = callSessionRegistry;
        this.interviewService = interviewService;
    }

    /**
     * 连接建立后：加入房间、同步房间状态，并在 AI 模式下推送题目。
     *
     * @param session 新建的会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String code = roomCodeOf(session);
        LoginUser loginUser = loginUserOf(session);
        if (code == null || loginUser == null) {
            log.warn("视频信令会话缺少握手属性，关闭连接，sessionId={}", session.getId());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        String displayName = displayNameOf(session, loginUser);

        JoinResult result;
        try {
            result = callSessionRegistry.join(code, session, loginUser, displayName);
        } catch (BizException e) {
            log.warn("加入视频房间失败，code={}，userId={}，原因={}", code, loginUser.id(), e.getMessage());
            callSessionRegistry.send(session, CallMessage.error(e.getMessage()));
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        } catch (Exception e) {
            log.error("加入视频房间异常，code={}，userId={}", code, loginUser.id(), e);
            callSessionRegistry.send(session, CallMessage.error("信令处理失败"));
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        CallRoom room = result.room();
        try {
            List<CallMessage.PeerInfo> peers = result.peers().stream().map(this::toPeerInfo).toList();
            callSessionRegistry.send(session, CallMessage.roomState(
                    code, room.mode(), room.practiceSessionId(), room.hostUserId(),
                    loginUser.id(), session.getId(), peers));
            callSessionRegistry.broadcast(code, CallMessage.peerJoined(toPeerInfo(result.self())), session.getId());
            log.info("视频信令连接建立，code={}，userId={}，peerId={}，mode={}",
                    code, loginUser.id(), session.getId(), room.mode());
            if (VideoRoom.MODE_AI.equals(room.mode()) && room.practiceSessionId() != null) {
                pushAiQuestion(session, room);
            }
        } catch (BizException e) {
            log.warn("视频房间初始化失败，code={}，原因={}", code, e.getMessage());
            callSessionRegistry.send(session, CallMessage.error(e.getMessage()));
        } catch (Exception e) {
            log.error("视频房间初始化异常，code={}", code, e);
            callSessionRegistry.send(session, CallMessage.error("信令处理失败"));
        }
    }

    /**
     * 处理客户端文本消息：解析 {@code {type, data}} 后分派。
     *
     * @param session 当前会话
     * @param message 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String code = roomCodeOf(session);
        LoginUser loginUser = loginUserOf(session);
        if (code == null || loginUser == null) {
            log.warn("视频信令会话缺少握手属性，关闭连接，sessionId={}", session.getId());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        String displayName = displayNameOf(session, loginUser);
        try {
            CallMessage payload = JsonUtils.fromJson(message.getPayload(), CallMessage.class);
            if (payload == null || TextUtils.isBlank(payload.type())) {
                throw BizException.badRequest("消息格式不正确：type 不能为空");
            }
            dispatch(session, code, loginUser, displayName, payload);
        } catch (BizException e) {
            log.warn("视频信令处理失败，code={}，peerId={}，原因={}", code, session.getId(), e.getMessage());
            callSessionRegistry.send(session, CallMessage.error(e.getMessage()));
        } catch (Exception e) {
            log.error("视频信令消息处理异常，code={}，peerId={}", code, session.getId(), e);
            callSessionRegistry.send(session, CallMessage.error("信令处理失败"));
        }
    }

    /**
     * 连接关闭后：离开房间并向剩余 peer 广播 {@code PEER_LEFT}。
     *
     * @param session 已关闭的会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String code = roomCodeOf(session);
        if (code == null) {
            return;
        }
        Peer left = callSessionRegistry.leave(code, session.getId());
        if (left != null) {
            callSessionRegistry.broadcast(code, CallMessage.peerLeft(left.peerId(), left.name()), null);
            log.info("视频信令连接关闭，code={}，peerId={}，status={}", code, session.getId(), status);
        }
    }

    /**
     * 按消息类型分派。
     *
     * @param session     当前会话
     * @param code        房间码
     * @param loginUser   登录用户
     * @param displayName 显示名
     * @param message     客户端消息
     */
    private void dispatch(WebSocketSession session, String code, LoginUser loginUser,
                          String displayName, CallMessage message) {
        String type = message.type().trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case CallMessage.TYPE_OFFER, CallMessage.TYPE_ANSWER, CallMessage.TYPE_ICE ->
                    forwardSignal(type, session, code, displayName, message.data());
            case CallMessage.TYPE_CHAT -> handleChat(session, code, displayName, message.data());
            case CallMessage.TYPE_HANGUP -> handleHangup(session, code);
            case CallMessage.TYPE_PING -> callSessionRegistry.send(session, CallMessage.pong());
            case CallMessage.TYPE_AI_NEXT -> handleAiNext(session, code, loginUser);
            default -> throw BizException.badRequest("不支持的信令类型：" + message.type());
        }
    }

    /**
     * 转发媒体协商信令：校验 data 非空，附加发送者 peerId/显示名后广播给其他 peer。
     *
     * @param type        信令类型
     * @param session     发送者会话
     * @param code        房间码
     * @param displayName 发送者显示名
     * @param data        原始 data
     */
    private void forwardSignal(String type, WebSocketSession session, String code,
                               String displayName, Object data) {
        if (isEmptyData(data)) {
            throw BizException.badRequest(type + " 消息缺少 data");
        }
        CallMessage forwarded = switch (type) {
            case CallMessage.TYPE_OFFER -> CallMessage.offer(data, session.getId(), displayName);
            case CallMessage.TYPE_ANSWER -> CallMessage.answer(data, session.getId(), displayName);
            case CallMessage.TYPE_ICE -> CallMessage.ice(data, session.getId(), displayName);
            default -> throw BizException.badRequest("不支持的信令类型：" + type);
        };
        callSessionRegistry.broadcast(code, forwarded, session.getId());
    }

    /**
     * 处理文字聊天：从 data 中读取 {@code text} 或直接使用字符串，广播给其他 peer。
     *
     * @param session     发送者会话
     * @param code        房间码
     * @param displayName 发送者显示名
     * @param data        原始 data
     */
    private void handleChat(WebSocketSession session, String code, String displayName, Object data) {
        String text = extractChatText(data);
        if (TextUtils.isBlank(text)) {
            throw BizException.badRequest("CHAT 消息缺少文本内容");
        }
        callSessionRegistry.broadcast(code,
                CallMessage.chat(session.getId(), displayName, text.trim()), session.getId());
    }

    /**
     * 处理挂断：先离开房间并广播 {@code PEER_LEFT}，再关闭会话。
     *
     * <p>先 leave 可保证随后的 {@code afterConnectionClosed} 不会重复广播离开事件。</p>
     *
     * @param session 当前会话
     * @param code    房间码
     */
    private void handleHangup(WebSocketSession session, String code) {
        Peer left = callSessionRegistry.leave(code, session.getId());
        if (left != null) {
            callSessionRegistry.broadcast(code, CallMessage.peerLeft(left.peerId(), left.name()), null);
        }
        closeQuietly(session, CloseStatus.NORMAL);
    }

    /**
     * 处理 AI 模式「下一题」：仅房主可用；取题成功下发 {@code AI_SAY}，
     * 409（已完成/无更多题目）下发 {@code AI_FINISHED}。
     *
     * @param session   当前会话
     * @param code      房间码
     * @param loginUser 登录用户
     */
    private void handleAiNext(WebSocketSession session, String code, LoginUser loginUser) {
        CallRoom room = callSessionRegistry.room(code);
        if (room == null) {
            throw BizException.notFound("视频房间不存在");
        }
        if (!VideoRoom.MODE_AI.equals(room.mode())) {
            throw BizException.badRequest("仅 AI 模式支持 AI_NEXT");
        }
        if (!loginUser.id().equals(room.hostUserId())) {
            throw BizException.forbidden("仅房主可以切换题目");
        }
        Long practiceSessionId = room.practiceSessionId();
        if (practiceSessionId == null) {
            throw BizException.badRequest("当前视频房间未关联练习会话");
        }
        try {
            CurrentQuestionVO question = interviewService.currentQuestion(practiceSessionId, room.hostUserId());
            callSessionRegistry.send(session, CallMessage.aiSay(question));
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.CONFLICT) {
                callSessionRegistry.send(session, CallMessage.aiFinished(practiceSessionId));
                return;
            }
            throw e;
        }
    }

    /**
     * AI 模式连接建立时确保练习已开始，并推送当前题目。
     *
     * <p>{@code interviewService.start} 在已开始/已结束时返回 409，此时继续取当前题；
     * 其余异常交由上层统一转成 {@code ERROR}。</p>
     *
     * @param session 当前会话
     * @param room    房间信令状态
     */
    private void pushAiQuestion(WebSocketSession session, CallRoom room) {
        Long practiceSessionId = room.practiceSessionId();
        try {
            interviewService.start(practiceSessionId);
        } catch (BizException e) {
            if (e.getErrorCode() != ErrorCode.CONFLICT) {
                throw e;
            }
            log.debug("练习会话无需重新开始，practiceSessionId={}，原因={}", practiceSessionId, e.getMessage());
        }
        CurrentQuestionVO question = interviewService.currentQuestion(practiceSessionId, room.hostUserId());
        callSessionRegistry.send(session, CallMessage.aiSay(question));
    }

    /**
     * 把注册表 peer 转成可序列化的公开信息。
     *
     * @param peer 房间 peer
     * @return 公开 peer 信息
     */
    private CallMessage.PeerInfo toPeerInfo(Peer peer) {
        return new CallMessage.PeerInfo(peer.peerId(), peer.userId(), peer.name());
    }

    /**
     * 判断信令 data 是否为空（null、空 Map 或空白字符串）。
     *
     * @param data 原始 data
     * @return 为空返回 {@code true}
     */
    private boolean isEmptyData(Object data) {
        if (data == null) {
            return true;
        }
        if (data instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (data instanceof String text) {
            return text.isBlank();
        }
        return false;
    }

    /**
     * 从 CHAT 的 data 中提取文本，兼容 {@code {"text":"..."}} 与直接字符串两种写法。
     *
     * @param data 原始 data
     * @return 聊天文本，无法提取时返回 {@code null}
     */
    private String extractChatText(Object data) {
        if (data instanceof String text) {
            return text;
        }
        if (data instanceof Map<?, ?> map) {
            Object value = map.get("text");
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    /**
     * 读取握手时写入的房间码。
     *
     * @param session 会话
     * @return 房间码，缺失时返回 {@code null}
     */
    private String roomCodeOf(WebSocketSession session) {
        Object value = session.getAttributes().get(CallHandshakeInterceptor.ATTR_ROOM_CODE);
        return value instanceof String text ? text : null;
    }

    /**
     * 读取握手时写入的登录用户。
     *
     * @param session 会话
     * @return 登录用户，缺失时返回 {@code null}
     */
    private LoginUser loginUserOf(WebSocketSession session) {
        Object value = session.getAttributes().get(CallHandshakeInterceptor.ATTR_LOGIN_USER);
        return value instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 读取握手时写入的显示名，缺失时回退到用户名。
     *
     * @param session   会话
     * @param loginUser 登录用户
     * @return 显示名
     */
    private String displayNameOf(WebSocketSession session, LoginUser loginUser) {
        Object value = session.getAttributes().get(CallHandshakeInterceptor.ATTR_DISPLAY_NAME);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return loginUser.username();
    }

    /**
     * 安静关闭会话，异常仅记录日志。
     *
     * @param session 会话
     * @param status  关闭状态
     */
    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.warn("关闭视频信令会话失败，sessionId={}", session.getId(), e);
        }
    }
}
