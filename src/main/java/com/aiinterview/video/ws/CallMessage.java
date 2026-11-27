package com.aiinterview.video.ws;

import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.dto.CurrentQuestionVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频房间信令消息，上下行统一为 {@code {type, data}}。
 *
 * <p>媒体协商（SDP/ICE）由前端通过本通道转发，后端不解析媒体内容：</p>
 * <ul>
 *     <li>客户端 → 服务端：{@code OFFER}、{@code ANSWER}、{@code ICE}、{@code CHAT}、
 *         {@code HANGUP}、{@code PING}、{@code AI_NEXT}；</li>
 *     <li>服务端 → 客户端：{@code ROOM_STATE}、{@code PEER_JOINED}、{@code PEER_LEFT}、
 *         {@code OFFER}、{@code ANSWER}、{@code ICE}、{@code CHAT}、{@code AI_SAY}、
 *         {@code AI_FINISHED}、{@code PONG}、{@code ERROR}。</li>
 * </ul>
 *
 * @param type 消息类型，非空
 * @param data 结构化数据，可为空
 */
public record CallMessage(String type, Object data) {

    /** 服务端 → 客户端：房间状态全量快照。 */
    public static final String TYPE_ROOM_STATE = "ROOM_STATE";
    /** 服务端 → 客户端：有 peer 加入。 */
    public static final String TYPE_PEER_JOINED = "PEER_JOINED";
    /** 服务端 → 客户端：有 peer 离开。 */
    public static final String TYPE_PEER_LEFT = "PEER_LEFT";
    /** 客户端 → 服务端 / 服务端 → 客户端：WebRTC Offer。 */
    public static final String TYPE_OFFER = "OFFER";
    /** 客户端 → 服务端 / 服务端 → 客户端：WebRTC Answer。 */
    public static final String TYPE_ANSWER = "ANSWER";
    /** 客户端 → 服务端 / 服务端 → 客户端：ICE candidate。 */
    public static final String TYPE_ICE = "ICE";
    /** 客户端 → 服务端 / 服务端 → 客户端：房间文字聊天。 */
    public static final String TYPE_CHAT = "CHAT";
    /** 客户端 → 服务端：挂断并关闭连接。 */
    public static final String TYPE_HANGUP = "HANGUP";
    /** 客户端 → 服务端：心跳请求。 */
    public static final String TYPE_PING = "PING";
    /** 服务端 → 客户端：心跳响应。 */
    public static final String TYPE_PONG = "PONG";
    /** 客户端 → 服务端：AI 模式请求下一题。 */
    public static final String TYPE_AI_NEXT = "AI_NEXT";
    /** 服务端 → 客户端：AI 下发当前题目。 */
    public static final String TYPE_AI_SAY = "AI_SAY";
    /** 服务端 → 客户端：AI 练习已无更多题目。 */
    public static final String TYPE_AI_FINISHED = "AI_FINISHED";
    /** 服务端 → 客户端：错误提示，连接通常保持。 */
    public static final String TYPE_ERROR = "ERROR";

    /** 房间内一个 peer 的公开信息（不包含 WebSocket 会话）。 */
    public record PeerInfo(String peerId, Long userId, String name) {
    }

    /**
     * 构造房间状态快照。
     *
     * @param code              房间码
     * @param mode              房间模式
     * @param practiceSessionId AI 模式关联的练习会话 id，可为空
     * @param selfId            当前连接自身的 peerId
     * @param peers             房间内全部 peer（含自己）
     * @return 房间状态消息
     */
    public static CallMessage roomState(String code, String mode, Long practiceSessionId,
                                        Long hostUserId, Long selfUserId,
                                        String selfId, List<PeerInfo> peers) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("mode", mode);
        data.put("practiceSessionId", practiceSessionId);
        data.put("hostUserId", hostUserId);
        data.put("selfUserId", selfUserId);
        data.put("selfId", selfId);
        data.put("peers", peers == null ? List.of() : peers);
        return new CallMessage(TYPE_ROOM_STATE, data);
    }

    /**
     * 构造「有 peer 加入」消息。
     *
     * @param peer 新加入的 peer
     * @return peer 加入消息
     */
    public static CallMessage peerJoined(PeerInfo peer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("peerId", peer.peerId());
        data.put("userId", peer.userId());
        data.put("name", peer.name());
        return new CallMessage(TYPE_PEER_JOINED, data);
    }

    /**
     * 构造「有 peer 离开」消息。
     *
     * @param peerId 离开的 peerId
     * @param name   离开者的显示名
     * @return peer 离开消息
     */
    public static CallMessage peerLeft(String peerId, String name) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("peerId", peerId);
        data.put("name", name);
        return new CallMessage(TYPE_PEER_LEFT, data);
    }

    /**
     * 构造转发的 Offer：原样携带 payload，并附加发送者身份。
     *
     * @param payload 客户端原始 data（通常是 {@code {type, sdp}}）
     * @param from    发送者 peerId
     * @param fromName 发送者显示名
     * @return Offer 消息
     */
    public static CallMessage offer(Object payload, String from, String fromName) {
        return forwarded(TYPE_OFFER, payload, from, fromName);
    }

    /**
     * 构造转发的 Answer：原样携带 payload，并附加发送者身份。
     *
     * @param payload 客户端原始 data（通常是 {@code {type, sdp}}）
     * @param from    发送者 peerId
     * @param fromName 发送者显示名
     * @return Answer 消息
     */
    public static CallMessage answer(Object payload, String from, String fromName) {
        return forwarded(TYPE_ANSWER, payload, from, fromName);
    }

    /**
     * 构造转发的 ICE candidate：原样携带 payload，并附加发送者身份。
     *
     * @param payload 客户端原始 data（通常是 candidate 对象）
     * @param from    发送者 peerId
     * @param fromName 发送者显示名
     * @return ICE 消息
     */
    public static CallMessage ice(Object payload, String from, String fromName) {
        return forwarded(TYPE_ICE, payload, from, fromName);
    }

    /**
     * 构造房间聊天消息。
     *
     * @param from     发送者 peerId
     * @param fromName 发送者显示名
     * @param text     聊天文本
     * @return 聊天消息
     */
    public static CallMessage chat(String from, String fromName, String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from);
        data.put("fromName", fromName);
        data.put("text", text);
        return new CallMessage(TYPE_CHAT, data);
    }

    /**
     * 构造 AI 下题消息。
     *
     * @param question 当前题目
     * @return AI 题目消息
     */
    public static CallMessage aiSay(CurrentQuestionVO question) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (question != null) {
            data.put("text", question.content());
            data.put("questionId", question.questionId());
            data.put("seq", question.seq());
            data.put("totalQuestions", question.totalQuestions());
        }
        return new CallMessage(TYPE_AI_SAY, data);
    }

    /**
     * 构造 AI 练习结束消息。
     *
     * @param practiceSessionId 练习会话 id
     * @return AI 结束消息
     */
    public static CallMessage aiFinished(Long practiceSessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("practiceSessionId", practiceSessionId);
        return new CallMessage(TYPE_AI_FINISHED, data);
    }

    /**
     * 构造错误消息。
     *
     * @param message 错误提示，空时使用兜底文案
     * @return 错误消息
     */
    public static CallMessage error(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", TextUtils.isBlank(message) ? "信令处理失败" : message);
        return new CallMessage(TYPE_ERROR, data);
    }

    /**
     * 构造心跳响应。
     *
     * @return PONG 消息
     */
    public static CallMessage pong() {
        return new CallMessage(TYPE_PONG, null);
    }

    /**
     * 转发媒体协商数据：Map 类型的 payload 平铺后附加 {@code from}/{@code fromName}，
     * 其他类型放入 {@code payload} 字段，避免丢失原始结构。
     *
     * @param type     消息类型
     * @param payload  原始 data
     * @param from     发送者 peerId
     * @param fromName 发送者显示名
     * @return 转发消息
     */
    private static CallMessage forwarded(String type, Object payload, String from, String fromName) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (payload instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> {
                if (key != null) {
                    data.put(String.valueOf(key), value);
                }
            });
        } else if (payload != null) {
            data.put("payload", payload);
        }
        data.put("from", from);
        data.put("fromName", fromName);
        return new CallMessage(type, data);
    }
}
