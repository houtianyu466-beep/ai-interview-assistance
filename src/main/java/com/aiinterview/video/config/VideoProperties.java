package com.aiinterview.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频面试配置，对应 {@code app.video.*}。
 *
 * <p>当前只包含 WebRTC 的 ICE 服务器列表。列表每项是一条完整的 STUN/TURN 地址，
 * 后端仅原样下发给前端，不参与媒体协商；媒体流全部由浏览器之间通过 WebRTC 传输。</p>
 *
 * <p>当配置项缺失时回退到 {@link #DEFAULT_ICE_SERVER}，保证本地开发无需额外配置。</p>
 */
@ConfigurationProperties(prefix = "app.video")
public class VideoProperties {

    /** 未配置 ICE 服务器时使用的默认 STUN 地址。 */
    public static final String DEFAULT_ICE_SERVER = "stun:stun.l.google.com:19302";

    /** ICE 服务器地址列表，例如 {@code stun:stun.l.google.com:19302}。 */
    private List<String> iceServers = new ArrayList<>(List.of(DEFAULT_ICE_SERVER));

    /**
     * 返回 ICE 服务器地址列表。
     *
     * @return ICE 服务器地址列表，可能为空但不会为 {@code null}
     */
    public List<String> getIceServers() {
        return iceServers;
    }

    /**
     * 设置 ICE 服务器地址列表，做防御性拷贝避免外部修改配置对象。
     *
     * @param iceServers ICE 服务器地址列表，允许为 {@code null}
     */
    public void setIceServers(List<String> iceServers) {
        this.iceServers = iceServers == null ? new ArrayList<>() : new ArrayList<>(iceServers);
    }
}
