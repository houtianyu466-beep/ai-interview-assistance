package com.aiinterview.video.dto;

import java.util.List;

/**
 * 前端创建 {@code RTCPeerConnection} 所需的 ICE 配置。
 *
 * <p>前端可直接把 {@link #iceServers()} 传入 {@code new RTCPeerConnection({ iceServers })}。</p>
 *
 * @param iceServers ICE 服务器列表
 */
public record IceConfigVO(List<IceServerVO> iceServers) {
}
