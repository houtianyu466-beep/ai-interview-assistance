import { onUnmounted, ref, type Ref } from 'vue'
import type { CallMessage, CallSocketStatus, CallSocketStatusDetail } from '@/types/video'

/** 心跳间隔：25s 发一次 PING，服务端回 PONG */
const HEARTBEAT_INTERVAL_MS = 25_000
/** 断线后最多重连 5 次 */
const MAX_RECONNECT_ATTEMPTS = 5
/** 指数退避起点 500ms，之后 1s / 2s / 4s / 5s（上限 5s） */
const BASE_RECONNECT_DELAY_MS = 500
const MAX_RECONNECT_DELAY_MS = 5_000
/** 连接稳定保持该时长后才清零重连计数，避免「连上就断」时无限重连 */
const STABLE_RESET_MS = 5_000

const NORMAL_CLOSE_CODE = 1000

export interface CallSocketConnectOptions {
  /** 登录 JWT，走 query 参数交给后端握手拦截器鉴权 */
  token: string
  /** 房间码 */
  code: string
  /** 显示名（后端会转给房间内其他人） */
  name?: string
  onMessage: (message: CallMessage) => void
  onStatus?: (status: CallSocketStatus, detail?: CallSocketStatusDetail) => void
  /**
   * 首次连接成功、以及每次断线重连成功后都会触发。
   * 重连后旧的 PeerConnection 已失效，调用方应在这里（或随后的 ROOM_STATE 里）重新协商。
   */
  onOpen?: () => void
}

export interface CallSocket {
  status: Ref<CallSocketStatus>
  connected: Ref<boolean>
  reconnectAttempt: Ref<number>
  lastError: Ref<string>
  connect: (options: CallSocketConnectOptions) => void
  /** 发送 `{ type, data }`；socket 未就绪时返回 false，不抛错 */
  send: (type: string, data?: unknown) => boolean
  close: () => void
}

/**
 * 把 `VITE_WS_BASE` 归一化为 `/ws/call` 的完整地址。
 *
 * - 为空：按当前页面协议 + host 拼 `ws(s)://<host>/ws/call`，开发环境由 Vite 代理 `/ws`；
 * - 已配置 `ws://` / `wss://`：直接拼 `/ws/call`；
 * - 误配成 `http(s)://`：自动换成 `ws(s)://`；
 * - 已写到 `/ws` 或 `/ws/call`：只补缺失的部分。
 */
export function resolveCallSocketUrl(token: string, code: string, name: string): string {
  const configured = String(import.meta.env.VITE_WS_BASE ?? '').trim()
  let base = configured

  if (!base) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    base = `${protocol}//${window.location.host}`
  } else if (base.startsWith('http://') || base.startsWith('https://')) {
    base = base.replace(/^http/, 'ws')
  }

  base = base.replace(/\/+$/, '')
  let url: string
  if (base.endsWith('/ws/call')) {
    url = base
  } else if (base.endsWith('/ws')) {
    url = `${base}/call`
  } else {
    url = `${base}/ws/call`
  }

  const params = new URLSearchParams({
    token,
    code,
    name: name ?? '',
  })
  return `${url}?${params.toString()}`
}

/**
 * `/ws/call` 长连接：心跳 + 断线重连 + 统一消息分发。
 *
 * 组件卸载时自动 close()，因此调用方无需额外清理（重复调用 close() 也安全）。
 */
export function useCallSocket(): CallSocket {
  const status = ref<CallSocketStatus>('idle')
  const connected = ref(false)
  const reconnectAttempt = ref(0)
  const lastError = ref('')

  let socket: WebSocket | null = null
  let options: CallSocketConnectOptions | null = null
  let heartbeatId: number | null = null
  let reconnectId: number | null = null
  let stableTimer: number | null = null
  let attempt = 0
  let manualClosed = false

  function updateStatus(next: CallSocketStatus, detail?: CallSocketStatusDetail): void {
    status.value = next
    options?.onStatus?.(next, detail)
  }

  function stopHeartbeat(): void {
    if (heartbeatId !== null) {
      window.clearInterval(heartbeatId)
      heartbeatId = null
    }
  }

  function startHeartbeat(): void {
    stopHeartbeat()
    heartbeatId = window.setInterval(() => {
      // socket 未 OPEN 时 send 返回 false，等 onclose 里的重连逻辑接管
      send('PING')
    }, HEARTBEAT_INTERVAL_MS)
  }

  function clearReconnectTimer(): void {
    if (reconnectId !== null) {
      window.clearTimeout(reconnectId)
      reconnectId = null
    }
  }

  function clearStableTimer(): void {
    if (stableTimer !== null) {
      window.clearTimeout(stableTimer)
      stableTimer = null
    }
  }

  /** 摘掉旧 socket 的所有回调并关闭，避免迟到的 close/error 影响新连接 */
  function teardownSocket(): void {
    stopHeartbeat()
    clearStableTimer()
    const stale = socket
    socket = null
    connected.value = false
    if (!stale) return
    stale.onopen = null
    stale.onmessage = null
    stale.onerror = null
    stale.onclose = null
    try {
      if (stale.readyState === WebSocket.OPEN || stale.readyState === WebSocket.CONNECTING) {
        stale.close(NORMAL_CLOSE_CODE, 'client close')
      }
    } catch {
      // 关闭时浏览器可能已回收 socket，忽略
    }
  }

  function handleRawMessage(raw: unknown): void {
    if (typeof raw !== 'string') return
    let parsed: unknown
    try {
      parsed = JSON.parse(raw)
    } catch {
      return
    }
    if (!parsed || typeof parsed !== 'object') return
    const message = parsed as CallMessage
    if (typeof message.type !== 'string' || !message.type) return

    // 服务端心跳探测：回 PONG 即可，不打扰业务回调
    if (message.type === 'PING') {
      send('PONG')
      return
    }
    // PONG 同样不需要业务处理
    if (message.type === 'PONG') return

    options?.onMessage(message)
  }

  function scheduleReconnect(closeCode: number, reason: string): void {
    if (attempt >= MAX_RECONNECT_ATTEMPTS) {
      lastError.value = `信令连接失败（${reason || '网络异常'}），请检查网络后重试`
      updateStatus('failed', { reason: lastError.value, code: closeCode, attempt })
      return
    }
    attempt += 1
    reconnectAttempt.value = attempt
    const delay = Math.min(BASE_RECONNECT_DELAY_MS * 2 ** (attempt - 1), MAX_RECONNECT_DELAY_MS)
    lastError.value = `连接已断开，${Math.round(delay / 100) / 10}s 后重连（第 ${attempt} 次）`
    updateStatus('reconnecting', {
      attempt,
      retryInMs: delay,
      reason: lastError.value,
      code: closeCode,
    })
    clearReconnectTimer()
    reconnectId = window.setTimeout(() => {
      reconnectId = null
      openSocket()
    }, delay)
  }

  function openSocket(): void {
    const current = options
    if (!current) return
    if (typeof WebSocket === 'undefined') {
      lastError.value = '当前浏览器不支持 WebSocket'
      updateStatus('failed', { reason: lastError.value })
      return
    }

    teardownSocket()
    updateStatus(attempt > 0 ? 'reconnecting' : 'connecting', { attempt })

    let next: WebSocket
    try {
      next = new WebSocket(resolveCallSocketUrl(current.token, current.code, current.name ?? ''))
    } catch {
      lastError.value = '信令地址无效，无法建立连接'
      updateStatus('failed', { reason: lastError.value })
      return
    }
    socket = next

    next.onopen = () => {
      if (socket !== next) return
      connected.value = true
      lastError.value = ''
      updateStatus('open')
      startHeartbeat()
      // 连续稳定一段时间后才认为重连成功，避免「刚连上就被关闭」导致无限重试
      clearStableTimer()
      stableTimer = window.setTimeout(() => {
        stableTimer = null
        attempt = 0
        reconnectAttempt.value = 0
      }, STABLE_RESET_MS)
      options?.onOpen?.()
    }

    next.onmessage = (event: MessageEvent) => {
      if (socket !== next) return
      handleRawMessage(event.data)
    }

    next.onerror = () => {
      // 浏览器随后一定会触发 onclose，重连逻辑统一放在那里，避免重复调度
      if (socket === next) lastError.value = '信令连接异常'
    }

    next.onclose = (event: CloseEvent) => {
      if (socket !== next) return
      socket = null
      connected.value = false
      stopHeartbeat()
      clearStableTimer()
      if (manualClosed) {
        updateStatus('closed', { code: event.code, reason: event.reason })
        return
      }
      scheduleReconnect(event.code, event.reason || '连接被中断')
    }
  }

  function connect(next: CallSocketConnectOptions): void {
    manualClosed = false
    options = next
    attempt = 0
    reconnectAttempt.value = 0
    lastError.value = ''
    clearReconnectTimer()

    if (!next.token || !next.code) {
      lastError.value = '缺少登录凭证或房间码，无法连接信令服务'
      updateStatus('failed', { reason: lastError.value })
      return
    }
    openSocket()
  }

  function send(type: string, data?: unknown): boolean {
    const active = socket
    if (!active || active.readyState !== WebSocket.OPEN) return false
    try {
      active.send(JSON.stringify(data === undefined ? { type } : { type, data }))
      return true
    } catch {
      return false
    }
  }

  function close(): void {
    manualClosed = true
    clearReconnectTimer()
    clearStableTimer()
    teardownSocket()
    stopHeartbeat()
    updateStatus('closed')
  }

  onUnmounted(close)

  return {
    status,
    connected,
    reconnectAttempt,
    lastError,
    connect,
    send,
    close,
  }
}
