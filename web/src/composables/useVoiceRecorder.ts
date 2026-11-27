import { onUnmounted, ref } from 'vue'

const UNSUPPORTED_MESSAGE = '当前浏览器不支持录音，请改用文字作答'
const PERMISSION_DENIED_MESSAGE = '麦克风权限被拒绝，请在浏览器地址栏允许麦克风后重试，或改用文字作答'
const NO_DEVICE_MESSAGE = '未检测到可用的麦克风，请连接麦克风后重试，或改用文字作答'
const DEVICE_BUSY_MESSAGE = '麦克风被其他程序占用，请关闭占用程序后重试，或改用文字作答'
const START_FAILED_MESSAGE = '录音启动失败，请稍后重试或改用文字作答'
const RECORD_FAILED_MESSAGE = '录音出错了，请重试或改用文字作答'

function isRecorderSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function' &&
    typeof MediaRecorder !== 'undefined'
  )
}

/** 优先 opus 的 webm，其次普通 webm；都不支持时交给浏览器选择默认格式 */
function preferredMimeType(): string {
  if (typeof MediaRecorder === 'undefined') return ''
  const candidates = ['audio/webm;codecs=opus', 'audio/webm']
  for (const type of candidates) {
    try {
      if (MediaRecorder.isTypeSupported(type)) return type
    } catch {
      // 某些浏览器没有实现 isTypeSupported，忽略并使用默认格式
    }
  }
  return ''
}

function mapRecorderError(caught: unknown): string {
  const name = caught instanceof DOMException ? caught.name : (caught as { name?: string } | null)?.name
  switch (name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
    case 'SecurityError':
      return PERMISSION_DENIED_MESSAGE
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return NO_DEVICE_MESSAGE
    case 'NotReadableError':
    case 'TrackStartError':
      return DEVICE_BUSY_MESSAGE
    default:
      return START_FAILED_MESSAGE
  }
}

function releaseStream(target: MediaStream | null): void {
  if (!target) return
  target.getTracks().forEach((track) => {
    try {
      track.stop()
    } catch {
      // 媒体轨道已结束时 stop 可能抛错，无需处理
    }
  })
}

/**
 * 浏览器录音（MediaRecorder）。
 *
 * - stop() 返回录音 Blob，供调用方转写；
 * - cancel() 丢弃本次录音并释放麦克风；
 * - 组件卸载时自动取消录音、清理计时器与媒体轨道。
 */
export function useVoiceRecorder() {
  const supported = isRecorderSupported()
  const recording = ref(false)
  const elapsedSeconds = ref(0)
  const error = ref(supported ? '' : UNSUPPORTED_MESSAGE)

  let stream: MediaStream | null = null
  let recorder: MediaRecorder | null = null
  let timerId: number | null = null
  let chunks: BlobPart[] = []
  let startToken = 0
  let discard = false
  let stopHandlers: {
    resolve: (blob: Blob) => void
    reject: (reason?: unknown) => void
  } | null = null

  function clearTimer(): void {
    if (timerId !== null) {
      window.clearInterval(timerId)
      timerId = null
    }
  }

  function rejectPendingStop(message = '录音已取消'): void {
    const handlers = stopHandlers
    stopHandlers = null
    handlers?.reject(new Error(message))
  }

  async function start(): Promise<void> {
    if (!supported) {
      error.value = UNSUPPORTED_MESSAGE
      return
    }
    if (recording.value) return

    error.value = ''
    elapsedSeconds.value = 0
    discard = false
    const token = ++startToken

    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true })
      // 等待授权期间被取消：直接释放刚拿到的麦克风
      if (token !== startToken) {
        releaseStream(media)
        return
      }
      stream = media

      const mimeType = preferredMimeType()
      const active = mimeType ? new MediaRecorder(media, { mimeType }) : new MediaRecorder(media)
      recorder = active
      chunks = []

      active.ondataavailable = (event: BlobEvent) => {
        if (event.data && event.data.size > 0) {
          chunks.push(event.data)
        }
      }

      active.onerror = () => {
        error.value = RECORD_FAILED_MESSAGE
        recording.value = false
        clearTimer()
        releaseStream(media)
        if (stream === media) stream = null
        if (recorder === active) recorder = null
        rejectPendingStop(error.value)
      }

      active.onstop = () => {
        clearTimer()
        releaseStream(media)
        if (stream === media) stream = null
        const blob = new Blob(chunks, { type: active.mimeType || 'audio/webm' })
        chunks = []
        if (recorder === active) recorder = null
        recording.value = false
        if (discard) {
          rejectPendingStop('录音已取消')
          return
        }
        const handlers = stopHandlers
        stopHandlers = null
        handlers?.resolve(blob)
      }

      active.start()
      recording.value = true
      elapsedSeconds.value = 0
      timerId = window.setInterval(() => {
        elapsedSeconds.value += 1
      }, 1000)
    } catch (caught) {
      if (token !== startToken) return
      const media = stream
      stream = null
      recorder = null
      recording.value = false
      clearTimer()
      releaseStream(media)
      error.value = mapRecorderError(caught)
    }
  }

  function stop(): Promise<Blob> {
    const active = recorder
    if (!active || active.state === 'inactive') {
      return Promise.reject(new Error('当前没有正在进行的录音'))
    }
    if (stopHandlers) {
      return Promise.reject(new Error('录音正在停止中'))
    }

    recording.value = false
    clearTimer()

    return new Promise<Blob>((resolve, reject) => {
      stopHandlers = { resolve, reject }
      try {
        active.stop()
      } catch (caught) {
        stopHandlers = null
        recorder = null
        const media = stream
        stream = null
        releaseStream(media)
        reject(caught)
      }
    })
  }

  function cancel(): void {
    startToken += 1
    discard = true
    recording.value = false
    elapsedSeconds.value = 0
    clearTimer()

    const active = recorder
    recorder = null
    if (active) {
      active.onstop = null
      active.ondataavailable = null
      active.onerror = null
      if (active.state !== 'inactive') {
        try {
          active.stop()
        } catch {
          // 停止失败时下面的轨道释放仍会执行
        }
      }
    }

    const media = stream
    stream = null
    releaseStream(media)
    chunks = []
    rejectPendingStop()
  }

  onUnmounted(cancel)

  return {
    supported,
    recording,
    elapsedSeconds,
    error,
    start,
    stop,
    cancel,
  }
}
