package com.aiinterview.video.dto;

import java.util.List;

/**
 * 单个 ICE 服务器配置，对应 WebRTC {@code RTCIceServer}。
 *
 * @param urls       ICE 服务器地址列表（STUN/TURN URL）
 * @param username   TURN 用户名，STUN 或未配置时为 {@code null}
 * @param credential TURN 凭证，STUN 或未配置时为 {@code null}
 */
public record IceServerVO(List<String> urls, String username, String credential) {
}
