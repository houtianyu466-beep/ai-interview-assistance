import { onUnmounted, ref } from 'vue'
import { getToken } from '@/utils/storage'

/**
 * 流式语音转写：WebSocket 长连接 + 浏览器端定长切片 + 服务端 VAD 断句。
 *
 * 数据流：
 * 麦克风 → AudioContext(16kHz) → AudioWorklet/ScriptProcessor → Int16 PCM
 * → 每 100ms 一帧经 WebSocket 上行 → 服务端 VAD 断句 → 逐句调用 ASR
 * → 服务端推送 TRANSCRIPT → 前端按顺序拼接。
 *
 * 与旧的一次性录音相比，说完一句（约 600ms 静音）就能拿到这一句的文字，
 * 不必等整段录音结束再上传转写。
 */

/** 上行帧时长：100ms，与后端 app.voice.stream.frame-ms 保持一致 */
const FRAME_MS = 100
/** 下行采样率：16kHz 单声道 */
const SAMPLE_RATE = 16000
/** 单帧采样点数 */
const FRAME_SAMPLES = (SAMPLE_RATE * FRAME_MS) / 1000

const UNSUPPORTED_MESSAGE = '当前浏览器不支持流式转写，请改用文字作答'
const PERMISSION_DENIED_MESSAGE = '麦克风权限被拒绝，请在地址栏允许后重试'
const CONNECT_FAILED_MESSAGE = '转写连接建立失败，请重试或改用文字作答'

/** AudioWorklet 处理器源码：把 Float32 采样转成块发给主线程 */
const WORKLET_SOURCE = `
class PcmCaptureProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0]
    if (input && input[0]) {
      this.port.postMessage(input[0].slice(0))
    }
    return true
  }
}
registerProcessor('pcm-capture', PcmCaptureProcessor)
`

export interface TranscriptSegment {
  seq: number
  text: string
  startMs: number
  endMs: number
  latencyMs: number
}

interface ServerMessage {
  type?: string
  text?: string
  seq?: number
  startMs?: number
  endMs?: number
  latencyMs?: number
  sampleRate?: number
  frameMs?: number
  message?: string
}

function isSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function' &&
    typeof WebSocket !== 'undefined' &&
    typeof AudioContext !== 'undefined'
  )
}

function buildUrl(language: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const token = getToken()
  const params = new URLSearchParams({ language })
  if (token) params.set('token', token)
  return `${protocol}://${window.location.host}/ws/asr?${params.toString()}`
}

/** 把 Float32 单声道采样降采样为 16kHz 的 Int16 PCM */
function toPcm16(input: Float32Array, sourceRate: number): Int16Array {
  const ratio = sourceRate / SAMPLE_RATE
  const outputLength = Math.floor(input.length / ratio)
  const output = new Int16Array(outputLength)
  for (let i = 0; i < outputLength; i += 1) {
    const start = Math.floor(i * ratio)
    const end = Math.min(input.length, Math.floor((i + 1) * ratio) || start + 1)
    let sum = 0
    for (let j = start; j < end; j += 1) sum += input[j]
    const sample = Math.max(-1, Math.min(1, sum / Math.max(1, end - start)))
    output[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff
  }
  return output
}

export function useStreamingTranscription() {
  const supported = isSupported()
  const connecting = ref(false)
  const recording = ref(false)
  const speechActive = ref(false)
  const error = ref(supported ? '' : UNSUPPORTED_MESSAGE)
  /** 累计的转写文本，可直接绑定到输入框 */
  const text = ref('')
  /** 最近一句的端到端延迟，用于界面展示「实时」效果 */
  const lastLatencyMs = ref(0)
  const segments = ref<TranscriptSegment[]>([])

  let socket: WebSocket | null = null
  let stream: MediaStream | null = null
  let context: AudioContext | null = null
  let sourceNode: AudioNode | null = null
  let workletNode: AudioWorkletNode | null = null
  let pending: Float32Array = new Float32Array(0)
  let stopped = false

  function reset(): void {
    text.value = ''
    segments.value = []
    lastLatencyMs.value = 0
    speechActive.value = false
  }

  function handleMessage(payload: string): void {
    let data: ServerMessage
    try {
      data = JSON.parse(payload) as ServerMessage
    } catch {
      return
    }
    switch (data.type) {
      case 'READY':
        if (typeof data.sampleRate === 'number' && data.sampleRate !== SAMPLE_RATE) {
          // 服务端采样率与前端不一致时以服务端为准，避免音调与识别偏差
          error.value = `服务端采样率为 ${data.sampleRate}，请检查配置`
        }
        break
      case 'SPEECH_START':
        speechActive.value = true
        break
      case 'TRANSCRIPT': {
        speechActive.value = false
        const piece = (data.text ?? '').trim()
        if (typeof data.latencyMs === 'number') lastLatencyMs.value = data.latencyMs
        if (!piece) return
        segments.value = [
          ...segments.value,
          {
            seq: data.seq ?? segments.value.length + 1,
            text: piece,
            startMs: data.startMs ?? 0,
            endMs: data.endMs ?? 0,
            latencyMs: data.latencyMs ?? 0,
          },
        ]
        // 中文句子之间不加空格，英文则保留空格
        const needsSpace = text.value.length > 0 && /[a-zA-Z]$/.test(text.value)
        text.value = text.value ? `${text.value}${needsSpace ? ' ' : ''}${piece}` : piece
        break
      }
      case 'ERROR':
        error.value = data.message || '转写失败，请继续说或改用文字作答'
        break
      default:
        break
    }
  }

  /** 等待服务端 READY 后开始推流，避免音频早于连接就绪被丢弃 */
  function waitReady(ws: WebSocket): Promise<void> {
    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error('timeout')), 8000)
      const onMessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(String(event.data)) as ServerMessage
          if (data.type === 'READY') {
            window.clearTimeout(timer)
            ws.removeEventListener('message', onMessage)
            resolve()
          }
        } catch {
          // 忽略非 JSON 消息
        }
      }
      ws.addEventListener('message', onMessage)
      ws.addEventListener('error', () => {
        window.clearTimeout(timer)
        reject(new Error('socket-error'))
      })
    })
  }

  async function startCapture(): Promise<void> {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    })
    context = new AudioContext({ sampleRate: 48000 })
    const sourceRate = context.sampleRate
    const source = context.createMediaStreamSource(stream)
    sourceNode = source

    const sendChunk = (chunk: Float32Array) => {
      const merged = new Float32Array(pending.length + chunk.length)
      merged.set(pending)
      merged.set(chunk, pending.length)
      pending = merged
      while (pending.length >= FRAME_SAMPLES * (sourceRate / SAMPLE_RATE)) {
        const take = Math.floor(FRAME_SAMPLES * (sourceRate / SAMPLE_RATE))
        const slice = pending.slice(0, take)
        pending = pending.slice(take)
        const pcm = toPcm16(slice, sourceRate)
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.send(pcm.buffer)
        }
      }
    }

    try {
      const blobUrl = URL.createObjectURL(
        new Blob([WORKLET_SOURCE], { type: 'application/javascript' }),
      )
      await context.audioWorklet.addModule(blobUrl)
      URL.revokeObjectURL(blobUrl)
      const node = new AudioWorkletNode(context, 'pcm-capture')
      node.port.onmessage = (event: MessageEvent<Float32Array>) => sendChunk(event.data)
      source.connect(node)
      // 不连到 destination，避免把麦克风回放到扬声器
      workletNode = node
    } catch {
      // 老浏览器没有 AudioWorklet，退回 ScriptProcessor
      const processor = context.createScriptProcessor(4096, 1, 1)
      processor.onaudioprocess = (event) => sendChunk(event.inputBuffer.getChannelData(0).slice(0))
      source.connect(processor)
      processor.connect(context.destination)
      workletNode = null
      sourceNode = processor
    }
    recording.value = true
  }

  async function start(): Promise<void> {
    if (!supported) {
      error.value = UNSUPPORTED_MESSAGE
      return
    }
    if (recording.value || connecting.value) return
    stopped = false
    error.value = ''
    connecting.value = true
    try {
      const ws = new WebSocket(buildUrl('zh'))
      ws.binaryType = 'arraybuffer'
      socket = ws
      ws.onmessage = (event) => handleMessage(String(event.data))
      await new Promise<void>((resolve, reject) => {
        ws.onopen = () => resolve()
        ws.onerror = () => reject(new Error('socket-error'))
      })
      await waitReady(ws)
      await startCapture()
      socket.onclose = () => {
        recording.value = false
        speechActive.value = false
      }
    } catch (caught) {
      const name = (caught as { name?: string } | null)?.name
      error.value =
        name === 'NotAllowedError' || name === 'SecurityError'
          ? PERMISSION_DENIED_MESSAGE
          : CONNECT_FAILED_MESSAGE
      await cleanup()
    } finally {
      connecting.value = false
    }
  }

  async function cleanup(): Promise<void> {
    try {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'STOP' }))
        socket.close()
      }
    } catch {
      // 已关闭时忽略
    }
    socket = null
    try {
      workletNode?.port.close()
      workletNode?.disconnect()
      sourceNode?.disconnect()
    } catch {
      // 节点已断开时忽略
    }
    workletNode = null
    sourceNode = null
    if (context) {
      try {
        await context.close()
      } catch {
        // 已关闭时忽略
      }
      context = null
    }
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
    pending = new Float32Array(0)
    recording.value = false
    speechActive.value = false
  }

  async function stop(): Promise<void> {
    if (stopped) return
    stopped = true
    await cleanup()
  }

  onUnmounted(() => {
    void cleanup()
  })

  return {
    supported,
    connecting,
    recording,
    speechActive,
    error,
    text,
    segments,
    lastLatencyMs,
    start,
    stop,
    reset,
  }
}
