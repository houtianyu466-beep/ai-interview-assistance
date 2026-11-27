package com.aiinterview.video.ws;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.security.LoginUser;
import com.aiinterview.video.dto.VideoRoomVO;
import com.aiinterview.video.entity.VideoRoom;
import com.aiinterview.video.service.VideoRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频房间 WebSocket 会话注册表。
 *
 * <p>以「房间码 → {@link CallRoom}」保存内存态连接关系，负责加入/离开与消息转发。
 * 房间元信息（模式、练习会话、房主）在首次加入时从数据库加载并缓存；房间无连接后自动移除。</p>
 *
 * <p>并发约定：对同一房间的加入/离开通过 {@link ConcurrentHashMap#compute} 串行化，
 * 房间内部再以 {@link CallRoom} 实例锁保护 peer 集合，避免超员与生命周期竞态。</p>
 */
@Component
public class CallSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallSessionRegistry.class);

    /** 真人对练房间的人数上限。 */
    public static final int MAX_HUMAN_PEERS = 2;

    private final Map<String, CallRoom> rooms = new ConcurrentHashMap<>();
    private final VideoRoomService videoRoomService;

    /**
     * 构造器注入。
     *
     * @param videoRoomService 视频房间服务，用于加载房间元信息
     */
    public CallSessionRegistry(VideoRoomService videoRoomService) {
        this.videoRoomService = videoRoomService;
    }

    /**
     * 加入房间。
     *
     * <p>规则：HUMAN 模式最多 {@value #MAX_HUMAN_PEERS} 人；AI 模式仅允许房主本人。
     * 同一 WebSocket 会话重复加入时幂等返回。</p>
     *
     * @param code    房间码
     * @param session 已建立的 WebSocket 会话
     * @param user    登录用户
     * @param name    显示名，空时回退到用户名
     * @return 加入结果，含房间、自身 peer 与当前全部 peer
     * @throws BizException 参数缺失（400）、AI 非房主（403）、真人房已满（409）
     */
    public JoinResult join(String code, WebSocketSession session, LoginUser user, String name) {
        if (TextUtils.isBlank(code) || session == null || user == null) {
            throw BizException.badRequest("加入视频房间的参数不完整");
        }
        String key = normalizeCode(code);
        CallRoom room = rooms.compute(key, (roomCode, existing) -> {
            CallRoom current = existing == null ? loadRoom(roomCode) : existing;
            synchronized (current) {
                if (current.peers.containsKey(session.getId())) {
                    return current;
                }
                if (VideoRoom.MODE_AI.equals(current.mode)) {
                    if (!user.id().equals(current.hostUserId)) {
                        throw BizException.forbidden("AI 模式仅房主可以进入");
                    }
                } else if (current.peers.size() >= MAX_HUMAN_PEERS) {
                    throw BizException.conflict("视频房间已满（ROOM_FULL），最多 2 人");
                }
                Peer peer = new Peer(session.getId(), user.id(), resolveName(name, user), session);
                current.peers.put(peer.peerId(), peer);
                log.info("用户 {} 加入视频房间 {}，peerId={}，当前人数={}",
                        user.id(), roomCode, session.getId(), current.peers.size());
            }
            return current;
        });
        Peer self = room.peers.get(session.getId());
        if (self == null) {
            throw BizException.conflict("加入视频房间失败，请重试");
        }
        return new JoinResult(room, self, room.peerList());
    }

    /**
     * 离开房间；房间已无连接时一并从注册表移除。
     *
     * @param code      房间码
     * @param sessionId 会话 id（peerId）
     * @return 被移除的 peer，未找到时返回 {@code null}
     */
    public Peer leave(String code, String sessionId) {
        if (TextUtils.isBlank(code) || TextUtils.isBlank(sessionId)) {
            return null;
        }
        Peer[] removed = new Peer[1];
        rooms.computeIfPresent(normalizeCode(code), (roomCode, room) -> {
            synchronized (room) {
                removed[0] = room.peers.remove(sessionId);
                if (room.peers.isEmpty()) {
                    log.info("视频房间 {} 已无在线连接，移除信令状态", roomCode);
                    return null;
                }
            }
            return room;
        });
        if (removed[0] != null) {
            log.info("peer {} 离开视频房间 {}，剩余人数={}", sessionId, code, peers(code).size());
        }
        return removed[0];
    }

    /**
     * 向房间内除指定会话外的所有 peer 广播消息。
     *
     * @param code           房间码
     * @param message        待发送消息
     * @param exceptSessionId 不接收该消息的会话 id，为 {@code null} 时发给全部
     */
    public void broadcast(String code, CallMessage message, String exceptSessionId) {
        if (TextUtils.isBlank(code) || message == null) {
            return;
        }
        CallRoom room = rooms.get(normalizeCode(code));
        if (room == null) {
            log.debug("视频房间 {} 没有在线连接，消息 {} 被丢弃", code, message.type());
            return;
        }
        String payload = JsonUtils.toJson(message);
        if (payload == null) {
            return;
        }
        for (Peer peer : room.peerList()) {
            if (exceptSessionId != null && exceptSessionId.equals(peer.peerId())) {
                continue;
            }
            sendText(peer.session(), payload);
        }
    }

    /**
     * 向单个会话发送消息。
     *
     * @param session 目标会话
     * @param message 待发送消息
     */
    public void send(WebSocketSession session, CallMessage message) {
        if (session == null || message == null) {
            return;
        }
        String payload = JsonUtils.toJson(message);
        if (payload != null) {
            sendText(session, payload);
        }
    }

    /**
     * 查询房间内全部 peer。
     *
     * @param code 房间码
     * @return peer 列表，房间不存在时返回空列表
     */
    public List<Peer> peers(String code) {
        CallRoom room = room(code);
        return room == null ? List.of() : room.peerList();
    }

    /**
     * 查询房间信令状态。
     *
     * @param code 房间码
     * @return 房间状态，不存在时返回 {@code null}
     */
    public CallRoom room(String code) {
        if (TextUtils.isBlank(code)) {
            return null;
        }
        return rooms.get(normalizeCode(code));
    }

    /**
     * 从数据库加载房间元信息并构建内存态。
     *
     * @param code 归一化后的房间码
     * @return 房间信令状态
     */
    private CallRoom loadRoom(String code) {
        VideoRoomVO room = videoRoomService.get(code);
        if (VideoRoom.STATUS_ENDED.equals(room.status())) {
            throw BizException.notFound("视频房间已结束");
        }
        log.info("初始化视频房间信令状态，code={}，mode={}", room.code(), room.mode());
        return new CallRoom(room.mode(), room.practiceSessionId(), room.hostUserId());
    }

    /**
     * 归一化房间码：去空白并转大写。
     *
     * @param code 原始房间码
     * @return 归一化后的房间码
     */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析显示名：空时回退到用户名，再空时回退到用户 id。
     *
     * @param name  客户端显示名
     * @param user  登录用户
     * @return 非空显示名
     */
    private String resolveName(String name, LoginUser user) {
        if (!TextUtils.isBlank(name)) {
            return name.trim();
        }
        if (!TextUtils.isBlank(user.username())) {
            return user.username();
        }
        return "用户" + user.id();
    }

    /**
     * 发送文本消息：对会话加锁并吞掉 IO 异常，避免单个连接故障影响其他连接。
     *
     * @param session 目标会话
     * @param payload 已序列化的 JSON
     */
    private void sendText(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.error("视频信令消息发送失败，sessionId={}", session.getId(), e);
        }
    }

    /**
     * 房间内一个在线 peer。
     *
     * @param peerId  peerId，等于 WebSocket sessionId
     * @param userId  用户 id
     * @param name    显示名
     * @param session WebSocket 会话
     */
    public record Peer(String peerId, Long userId, String name, WebSocketSession session) {
    }

    /**
     * 房间信令状态。
     */
    public static final class CallRoom {

        /** 房间模式：AI | HUMAN */
        private final String mode;
        /** AI 模式关联的练习会话 id */
        private final Long practiceSessionId;
        /** 房主用户 id */
        private final Long hostUserId;
        /** peerId → peer */
        private final Map<String, Peer> peers = new ConcurrentHashMap<>();

        /**
         * 构造房间信令状态。
         *
         * @param mode              房间模式
         * @param practiceSessionId 练习会话 id，可为空
         * @param hostUserId        房主用户 id
         */
        public CallRoom(String mode, Long practiceSessionId, Long hostUserId) {
            this.mode = mode;
            this.practiceSessionId = practiceSessionId;
            this.hostUserId = hostUserId;
        }

        /**
         * 返回房间模式。
         *
         * @return 房间模式
         */
        public String mode() {
            return mode;
        }

        /**
         * 返回 AI 模式关联的练习会话 id。
         *
         * @return 练习会话 id，可为空
         */
        public Long practiceSessionId() {
            return practiceSessionId;
        }

        /**
         * 返回房主用户 id。
         *
         * @return 房主用户 id
         */
        public Long hostUserId() {
            return hostUserId;
        }

        /**
         * 返回 peer 集合的只读快照。
         *
         * @return peer 映射
         */
        public Map<String, Peer> peers() {
            return Map.copyOf(peers);
        }

        /**
         * 返回 peer 列表快照，用于安全遍历广播。
         *
         * @return peer 列表
         */
        public List<Peer> peerList() {
            return List.copyOf(peers.values());
        }
    }

    /**
     * 加入房间结果。
     *
     * @param room  房间信令状态
     * @param self  当前连接自身
     * @param peers 房间内全部 peer（含自己）
     */
    public record JoinResult(CallRoom room, Peer self, List<Peer> peers) {
    }
}
