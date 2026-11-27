import type { IceServer } from '@/types/video'

/** 是否支持 WebRTC（1:1 真人模式必需） */
export function isWebRTCSupported(): boolean {
  return typeof window !== 'undefined' && typeof window.RTCPeerConnection === 'function'
}

/** 是否支持采集摄像头/麦克风 */
export function isGetUserMediaSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function'
  )
}

export interface WebRTCCreateOptions {
  /**
   * perfect negotiation 角色：
   * - 房间创建者（房主）为 impolite：收到冲突 offer 时直接忽略；
   * - 加入者为 polite：冲突时回滚本地 offer 并接受对方 offer。
   */
  polite: boolean
  onRemoteStream: (stream: MediaStream) => void
  onConnectionState: (state: RTCPeerConnectionState) => void
  /** 本地 ICE candidate，调用方通过信令转发给对方 */
  onIceCandidate?: (candidate: RTCIceCandidateInit) => void
  /** 协商/连接异常的可读提示，调用方用于 UI 兜底 */
  onError?: (message: string) => void
}

export interface WebRTCPeer {
  readonly polite: boolean
  /** 当前信令状态，调用方据此避免重复发 offer */
  signalingState: () => RTCSignalingState
  /** 创建 offer 并写入本地描述，返回待发送的 SDP；失败返回 null */
  createOffer: () => Promise<RTCSessionDescriptionInit | null>
  /** 处理对方 offer，返回 answer；冲突被忽略或失败时返回 null */
  handleOffer: (sdp: RTCSessionDescriptionInit) => Promise<RTCSessionDescriptionInit | null>
  handleAnswer: (sdp: RTCSessionDescriptionInit) => Promise<void>
  /** 处理对方 ICE；remoteDescription 就绪前会先入队 */
  handleIce: (candidate: RTCIceCandidateInit | string | null | undefined) => Promise<void>
  /** 开启/关闭麦克风轨道，返回是否找到对应轨道 */
  toggleMic: (enabled: boolean) => boolean
  toggleCamera: (enabled: boolean) => boolean
  close: () => void
}

function toRtcIceServers(iceServers: IceServer[] | null | undefined): RTCIceServer[] {
  if (!Array.isArray(iceServers)) return []
  return iceServers
    .filter((server) => {
      if (!server || server.urls == null) return false
      return Array.isArray(server.urls) ? server.urls.length > 0 : Boolean(String(server.urls).trim())
    })
    .map((server) => {
      const normalized: RTCIceServer = { urls: server.urls }
      if (server.username) normalized.username = server.username
      if (server.credential) normalized.credential = server.credential
      return normalized
    })
}

function normalizeCandidate(
  candidate: RTCIceCandidateInit | string | null | undefined,
): RTCIceCandidateInit | null {
  if (candidate == null) return null
  if (typeof candidate === 'string') {
    return candidate ? { candidate } : null
  }
  if (typeof candidate !== 'object' || !candidate.candidate) return null
  const init: RTCIceCandidateInit = { candidate: candidate.candidate }
  if (candidate.sdpMid != null) init.sdpMid = candidate.sdpMid
  if (candidate.sdpMLineIndex != null) init.sdpMLineIndex = candidate.sdpMLineIndex
  if (candidate.usernameFragment != null) init.usernameFragment = candidate.usernameFragment
  return init
}

function parseSdp(sdp: unknown): RTCSessionDescriptionInit | null {
  if (!sdp || typeof sdp !== 'object') return null
  const value = sdp as RTCSessionDescriptionInit
  if (typeof value.sdp !== 'string' || !value.sdp) return null
  if (value.type !== 'offer' && value.type !== 'answer') return null
  return { type: value.type, sdp: value.sdp }
}

/**
 * 单个 1:1 的 WebRTC 连接（perfect negotiation）。
 *
 * 用法：房主 `createOffer()` → 对方 `handleOffer(offer)` 得到 answer → 房主 `handleAnswer(answer)`；
 * 双方把各自的 `onIceCandidate` 通过信令转发给对方并调用 `handleIce()`。
 */
export function createPeer(
  iceServers: IceServer[],
  localStream: MediaStream | null,
  options: WebRTCCreateOptions,
): WebRTCPeer {
  const polite = options.polite
  let pc: RTCPeerConnection | null = null
  let makingOffer = false
  let ignoreOffer = false
  let closed = false
  let remoteStream: MediaStream | null = null
  /** remoteDescription 就绪前收到的 candidate 先缓存 */
  const pendingCandidates: RTCIceCandidateInit[] = []
  let lastErrorMessage = ''

  function reportError(message: string, error?: unknown): void {
    // 同一类错误短时间内可能重复触发，做个简单去重避免刷屏
    if (lastErrorMessage === message) return
    lastErrorMessage = message
    console.warn(`[webrtc] ${message}`, error ?? '')
    options.onError?.(message)
  }

  function pickLocalTracks(kind: 'audio' | 'video'): MediaStreamTrack[] {
    const tracks = localStream?.getTracks().filter((track) => track.kind === kind) ?? []
    if (tracks.length) return tracks
    const senders = pc?.getSenders() ?? []
    return senders
      .map((sender) => sender.track)
      .filter((track): track is MediaStreamTrack => Boolean(track) && track!.kind === kind)
  }

  function applyTrackEnabled(kind: 'audio' | 'video', enabled: boolean): boolean {
    const tracks = pickLocalTracks(kind)
    tracks.forEach((track) => {
      track.enabled = enabled
    })
    return tracks.length > 0
  }

  async function flushPendingCandidates(): Promise<void> {
    if (!pc || !pc.remoteDescription) return
    while (pendingCandidates.length) {
      const candidate = pendingCandidates.shift()
      if (!candidate) continue
      try {
        await pc.addIceCandidate(candidate)
      } catch (error) {
        if (!ignoreOffer) console.warn('[webrtc] 添加缓存的 ICE candidate 失败', error)
      }
    }
  }

  async function applyRemoteDescription(description: RTCSessionDescriptionInit): Promise<void> {
    if (!pc) return
    try {
      await pc.setRemoteDescription(description)
    } catch (error) {
      // polite 一侧遇到 offer 冲突时，先回滚本地 offer 再重试（部分浏览器不自动回滚）
      if (!polite || description.type !== 'offer') throw error
      try {
        await pc.setLocalDescription({ type: 'rollback' })
      } catch {
        // 不支持 rollback 时继续尝试，下面的重试会给出真实错误
      }
      await pc.setRemoteDescription(description)
    }
    await flushPendingCandidates()
  }

  try {
    pc = new RTCPeerConnection({ iceServers: toRtcIceServers(iceServers) })
  } catch (error) {
    reportError('无法初始化 WebRTC 连接，请检查浏览器设置', error)
  }

  if (pc) {
    localStream?.getTracks().forEach((track) => {
      try {
        pc?.addTrack(track, localStream)
      } catch (error) {
        console.warn('[webrtc] 添加本地轨道失败', error)
      }
    })

    // 没有本地轨道时也声明收发方向，保证仍能收到对方画面
    if (!localStream || localStream.getTracks().length === 0) {
      try {
        pc.addTransceiver('audio', { direction: 'recvonly' })
        pc.addTransceiver('video', { direction: 'recvonly' })
      } catch {
        // 老浏览器可能不支持 addTransceiver，忽略
      }
    }

    pc.onicecandidate = (event) => {
      if (event.candidate?.candidate) {
        options.onIceCandidate?.(event.candidate.toJSON())
      }
    }

    pc.ontrack = (event) => {
      if (event.streams && event.streams[0]) {
        remoteStream = event.streams[0]
      } else {
        remoteStream = remoteStream ?? new MediaStream()
        if (!remoteStream.getTracks().some((track) => track.id === event.track.id)) {
          remoteStream.addTrack(event.track)
        }
      }
      options.onRemoteStream(remoteStream)
    }

    pc.onconnectionstatechange = () => {
      if (!pc) return
      const state = pc.connectionState
      options.onConnectionState(state)
      if (state === 'failed') {
        reportError('媒体连接失败，可能需要配置 TURN 服务或切换网络')
      }
    }
  }

  function createOffer(): Promise<RTCSessionDescriptionInit | null> {
    if (!pc || closed) return Promise.resolve(null)
    return (async () => {
      try {
        makingOffer = true
        const offer = await pc!.createOffer()
        await pc!.setLocalDescription(offer)
        const local = pc!.localDescription
        return local ? { type: local.type, sdp: local.sdp } : null
      } catch (error) {
        reportError('发起视频连接失败，请重试', error)
        return null
      } finally {
        makingOffer = false
      }
    })()
  }

  async function handleOffer(sdp: RTCSessionDescriptionInit): Promise<RTCSessionDescriptionInit | null> {
    const description = parseSdp(sdp)
    if (!pc || closed || !description || description.type !== 'offer') return null

    const offerCollision = makingOffer || pc.signalingState !== 'stable'
    // 房主（impolite）忽略冲突 offer；加入者（polite）回滚后继续
    ignoreOffer = !polite && offerCollision
    if (ignoreOffer) return null

    try {
      await applyRemoteDescription(description)
      ignoreOffer = false
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      const local = pc.localDescription
      return local ? { type: local.type, sdp: local.sdp } : null
    } catch (error) {
      reportError('处理对方的连接请求失败', error)
      return null
    }
  }

  async function handleAnswer(sdp: RTCSessionDescriptionInit): Promise<void> {
    const description = parseSdp(sdp)
    if (!pc || closed || !description || description.type !== 'answer') return
    // 只接受对自己 offer 的 answer，过期的 answer 直接丢弃
    if (pc.signalingState !== 'have-local-offer') return
    try {
      await applyRemoteDescription(description)
      ignoreOffer = false
    } catch (error) {
      reportError('确认对方连接失败', error)
    }
  }

  async function handleIce(
    candidate: RTCIceCandidateInit | string | null | undefined,
  ): Promise<void> {
    const init = normalizeCandidate(candidate)
    if (!init || !pc || closed) return
    if (!pc.remoteDescription) {
      pendingCandidates.push(init)
      return
    }
    try {
      await pc.addIceCandidate(init)
    } catch (error) {
      // 冲突 offer 被忽略时，对端 candidate 报错属预期
      if (!ignoreOffer) console.warn('[webrtc] 添加 ICE candidate 失败', error)
    }
  }

  function toggleMic(enabled: boolean): boolean {
    return applyTrackEnabled('audio', enabled)
  }

  function toggleCamera(enabled: boolean): boolean {
    return applyTrackEnabled('video', enabled)
  }

  function close(): void {
    closed = true
    pendingCandidates.length = 0
    remoteStream = null
    if (!pc) return
    pc.onicecandidate = null
    pc.ontrack = null
    pc.onconnectionstatechange = null
    try {
      pc.close()
    } catch {
      // 已经关闭时忽略
    }
    pc = null
  }

  return {
    polite,
    signalingState: () => pc?.signalingState ?? 'closed',
    createOffer,
    handleOffer,
    handleAnswer,
    handleIce,
    toggleMic,
    toggleCamera,
    close,
  }
}
