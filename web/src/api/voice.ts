import { postForm } from '@/api/http'

/** 后端语音转写返回（字段与 /v1/voice/transcribe 契约保持一致） */
export interface VoiceTranscription {
  text: string
  provider: string
  model: string
  latencyMs?: number
  audioPath?: string
}

/**
 * 上传一段录音并转写为文字。
 *
 * 后端会把音频落盘后调用 ASR；返回的 audioPath 可随答案一起提交，
 * 复盘页默认只展示文本，不展示音频。
 */
export function transcribeAudio(blob: Blob, filename = 'answer.webm'): Promise<VoiceTranscription> {
  const form = new FormData()
  form.append('file', blob, filename)
  form.append('language', 'zh')
  return postForm<VoiceTranscription>('/v1/voice/transcribe', form)
}
