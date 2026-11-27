package com.aiinterview.video.ws;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.security.JwtTokenProvider;
import com.aiinterview.security.LoginUser;
import com.aiinterview.security.TokenPayload;
import com.aiinterview.video.dto.VideoRoomVO;
import com.aiinterview.video.entity.VideoRoom;
import com.aiinterview.video.service.VideoRoomService;
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
 * 视频房间 WebSocket 握手拦截器：在 HTTP Upgrade 阶段完成令牌鉴权与房间可用性校验。
 *
 * <p>连接地址形如：{@code ws://host/ws/call?token=<JWT>&code=<房间码>&name=<显示名>}。
 * 鉴权失败返回 401，房间不存在或已结束返回 404，握手不会建立。</p>
 */
@Component
public class CallHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CallHandshakeInterceptor.class);

    /** 会话属性键：登录用户。 */
    public static final String ATTR_LOGIN_USER = "loginUser";
    /** 会话属性键：房间码（已归一化为大写）。 */
    public static final String ATTR_ROOM_CODE = "roomCode";
    /** 会话属性键：显示名。 */
    public static final String ATTR_DISPLAY_NAME = "displayName";

    private final JwtTokenProvider jwtTokenProvider;
    private final VideoRoomService videoRoomService;

    /**
     * 构造器注入。
     *
     * @param jwtTokenProvider JWT 解析器
     * @param videoRoomService 视频房间服务，用于校验房间存在且未结束
     */
    public CallHandshakeInterceptor(JwtTokenProvider jwtTokenProvider, VideoRoomService videoRoomService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.videoRoomService = videoRoomService;
    }

    /**
     * 握手前校验：query 参数 → 令牌 → 房间状态。
     *
     * @param request    HTTP 请求
     * @param response   HTTP 响应，校验失败时写入状态码
     * @param wsHandler  WebSocket 处理器
     * @param attributes 会话属性，校验通过后写入 loginUser、roomCode、displayName
     * @return 校验通过返回 {@code true}
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token;
        String code;
        String name;
        try {
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams();
            token = params.getFirst("token");
            code = params.getFirst("code");
            name = params.getFirst("name");
        } catch (RuntimeException e) {
            log.warn("视频信令握手参数解析失败，uri={}", request.getURI(), e);
            return reject(response, HttpStatus.UNAUTHORIZED, "握手参数解析失败");
        }

        if (TextUtils.isBlank(token) || TextUtils.isBlank(code)) {
            log.warn("视频信令握手缺少 token 或 code，uri={}", request.getURI());
            return reject(response, HttpStatus.UNAUTHORIZED, "缺少 token 或 code 参数");
        }

        LoginUser loginUser;
        try {
            TokenPayload payload = jwtTokenProvider.parseToken(token);
            if (payload == null || payload.userId() == null) {
                log.warn("视频信令握手令牌载荷为空，code={}", code);
                return reject(response, HttpStatus.UNAUTHORIZED, "访问令牌无效");
            }
            loginUser = new LoginUser(payload.userId(), payload.username(), payload.role());
        } catch (BizException e) {
            log.warn("视频信令握手鉴权失败，code={}，原因={}", code, e.getMessage());
            return reject(response, HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (RuntimeException e) {
            log.error("视频信令握手鉴权异常，code={}", code, e);
            return reject(response, HttpStatus.UNAUTHORIZED, "鉴权失败，请重新登录");
        }

        VideoRoomVO room;
        try {
            room = videoRoomService.get(code);
        } catch (BizException e) {
            log.warn("视频信令握手房间校验失败，code={}，原因={}", code, e.getMessage());
            return reject(response, HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            log.error("视频信令握手房间校验异常，code={}", code, e);
            return reject(response, HttpStatus.NOT_FOUND, "视频房间不存在");
        }
        if (VideoRoom.STATUS_ENDED.equals(room.status())) {
            log.warn("视频信令握手被拒绝，房间已结束，code={}", code);
            return reject(response, HttpStatus.NOT_FOUND, "视频房间已结束");
        }

        attributes.put(ATTR_LOGIN_USER, loginUser);
        attributes.put(ATTR_ROOM_CODE, room.code());
        attributes.put(ATTR_DISPLAY_NAME, resolveDisplayName(name, loginUser));
        log.info("视频信令握手通过，userId={}，code={}", loginUser.id(), room.code());
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
     * 解析显示名：优先使用客户端传入的 name，为空时回退到用户名。
     *
     * @param name      客户端传入的显示名
     * @param loginUser 登录用户
     * @return 非空的显示名
     */
    private String resolveDisplayName(String name, LoginUser loginUser) {
        if (!TextUtils.isBlank(name)) {
            return name.trim();
        }
        String username = loginUser.username();
        return TextUtils.isBlank(username) ? "用户" + loginUser.id() : username;
    }

    /**
     * 拒绝握手：写入 HTTP 状态码并返回 {@code false}。
     *
     * @param response HTTP 响应
     * @param status   状态码
     * @param reason   失败原因，仅用于日志
     * @return 恒为 {@code false}
     */
    private boolean reject(ServerHttpResponse response, HttpStatus status, String reason) {
        response.setStatusCode(status);
        log.debug("拒绝视频信令握手，status={}，reason={}", status.value(), reason);
        return false;
    }
}
