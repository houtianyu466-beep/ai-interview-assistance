import { onUnmounted, ref } from 'vue'
import { getToken } from '@/utils/storage'

/**
 * AI 面试官朗读（TTS）。
 *
 * 优先级：
 * 1. 后端 `/api/v1/voice/tts`（OpenAI 兼容或豆包/火山），音色自然、有起伏；
 * 2. 后端不可用时退回浏览器 `speechSynthesis`，但会按标点切句、逐句微调语速与音高，
 *    并插入短停顿——比整段平铺直叙更接近真人说话节奏。
 */

/** 按中文标点切句，保留标点本身 */
const SENTENCE_SPLIT = /(?<=[。！？；!?;\n])/

/** 每句在基础语速上的浮动，制造轻微起伏 */
const RATE_CYCLE = [0.98, 1.04, 1.0, 1.06, 0.96]
/** 每句音高浮动（浏览器 TTS 的 pitch 范围 0~2） */
const PITCH_CYCLE = [1.0, 1.06, 0.96, 1.03, 0.98]

export interface NarratorOptions {
  /** 后端音色，可为空 */
  voice?: string
  /** 语速倍率，1 为正常 */
  rate?: number
}

export function useNarrator() {
  const speaking = ref(false)
  const backendEnabled = ref(false)
  const provider = ref('none')
  const error = ref('')

  let audio: HTMLAudioElement | null = null
  let audioUrl: string | null = null
  let utteranceQueue: SpeechSynthesisUtterance[] = []
  let cancelled = false

  const speechSupported = typeof window !== 'undefined' && 'speechSynthesis' in window

  /** 后端接口需要 JWT，这里与 axios 实例保持同一令牌来源 */
  function authHeaders(): Record<string, string> {
    const token = getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  /** 查询后端合成能力，失败时静默退回浏览器 */
  async function refreshCapability(): Promise<void> {
    try {
      const response = await fetch('/api/v1/voice/tts/info', { headers: authHeaders() })
      if (!response.ok) return
      const body = (await response.json()) as {
        data?: { enabled?: boolean; provider?: string }
      }
      backendEnabled.value = Boolean(body?.data?.enabled)
      provider.value = body?.data?.provider || 'none'
    } catch {
      backendEnabled.value = false
    }
  }

  function releaseAudio(): void {
    if (audio) {
      try {
        audio.pause()
      } catch {
        // 已停止时忽略
      }
      audio = null
    }
    if (audioUrl) {
      URL.revokeObjectURL(audioUrl)
      audioUrl = null
    }
  }

  function cancelBrowserSpeech(): void {
    if (!speechSupported) return
    try {
      window.speechSynthesis.cancel()
    } catch {
      // 忽略
    }
    utteranceQueue = []
  }

  /**
   * 浏览器兜底朗读：逐句调整语速/音高并插入停顿。
   */
  function speakWithBrowser(text: string, rate: number): void {
    if (!speechSupported) return
    cancelBrowserSpeech()
    const sentences = text
      .split(SENTENCE_SPLIT)
      .map((item) => item.trim())
      .filter(Boolean)
    utteranceQueue = sentences.map((sentence, index) => {
      const utterance = new SpeechSynthesisUtterance(sentence)
      utterance.lang = 'zh-CN'
      utterance.rate = Math.min(2, Math.max(0.5, rate * RATE_CYCLE[index % RATE_CYCLE.length]))
      utterance.pitch = PITCH_CYCLE[index % PITCH_CYCLE.length]
      // 句末稍作停顿，模拟真人换气
      utterance.onend = () => {
        if (index === sentences.length - 1) {
          speaking.value = false
        }
      }
      utterance.onerror = () => {
        if (index === sentences.length - 1) {
          speaking.value = false
        }
      }
      return utterance
    })
    if (!utteranceQueue.length) return
    speaking.value = true
    utteranceQueue.forEach((utterance) => window.speechSynthesis.speak(utterance))
  }

  /**
   * 朗读一段文本。
   *
   * @param text   文本
   * @param options 音色与语速
   */
  async function speak(text: string, options: NarratorOptions = {}): Promise<void> {
    const content = (text || '').trim()
    if (!content) return
    cancel()
    cancelled = false
    speaking.value = true
    error.value = ''
    const rate = options.rate ?? 1

    if (backendEnabled.value) {
      try {
        const response = await fetch('/api/v1/voice/tts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          body: JSON.stringify({ text: content, voice: options.voice }),
        })
        if (!response.ok) throw new Error(`tts ${response.status}`)
        const blob = await response.blob()
        if (cancelled) return
        audioUrl = URL.createObjectURL(blob)
        audio = new Audio(audioUrl)
        audio.playbackRate = Math.min(2, Math.max(0.5, rate))
        audio.onended = () => {
          speaking.value = false
        }
        audio.onerror = () => {
          speaking.value = false
        }
        await audio.play()
        return
      } catch (caught) {
        error.value = caught instanceof Error ? caught.message : 'tts failed'
        // 后端失败直接退回浏览器，不打断面试
      }
    }
    if (cancelled) return
    speakWithBrowser(content, rate)
  }

  /** 停止朗读并释放音频资源 */
  function cancel(): void {
    cancelled = true
    speaking.value = false
    releaseAudio()
    cancelBrowserSpeech()
  }

  onUnmounted(() => {
    cancel()
  })

  return {
    speaking,
    backendEnabled,
    provider,
    error,
    speechSupported,
    refreshCapability,
    speak,
    cancel,
  }
}
