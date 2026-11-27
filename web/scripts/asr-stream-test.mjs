/**
 * 流式转写端到端测试（不依赖浏览器）。
 *
 * 做法：用系统语音合成生成一段中文 WAV → 重采样为 16kHz 单声道 PCM16 →
 * 按 100ms 一帧通过 /ws/asr 推给后端 → 收集 TRANSCRIPT 文本。
 *
 * 用法：
 *   node scripts/asr-stream-test.mjs [wav路径]
 * 不传路径时自动用 PowerShell 生成一段测试语音。
 */
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const API = 'http://127.0.0.1:8080/api/v1'
const WS = 'ws://127.0.0.1:8080/ws/asr'
const SAMPLE_RATE = 16000
const FRAME_MS = 100

function generateSpeechWav() {
  const dir = mkdtempSync(join(tmpdir(), 'asr-test-'))
  const file = join(dir, 'speech.wav')
  const script = `
Add-Type -AssemblyName System.Speech
$sp = New-Object System.Speech.Synthesis.SpeechSynthesizer
$sp.SetOutputToWaveFile('${file.replace(/\\/g, '\\\\')}')
$sp.Speak('${process.env.ASR_TEST_TEXT || '你好，我是张伟，有五年 Java 后端开发经验，熟悉 Spring Boot 和 Redis。'}')
$sp.Dispose()
`
  writeFileSync(join(dir, 'gen.ps1'), '\ufeff' + script, 'utf8')
  execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(dir, 'gen.ps1')], {
    stdio: 'ignore',
  })
  return file
}

/** 解析 WAV：返回 {sampleRate, channels, bits, samples: Float32Array} */
function readWav(path) {
  const buf = readFileSync(path)
  if (buf.toString('ascii', 0, 4) !== 'RIFF' || buf.toString('ascii', 8, 12) !== 'WAVE') {
    throw new Error('不是合法的 WAV 文件')
  }
  let offset = 12
  let fmt = null
  let data = null
  while (offset + 8 <= buf.length) {
    const id = buf.toString('ascii', offset, offset + 4)
    const size = buf.readUInt32LE(offset + 4)
    const body = offset + 8
    if (id === 'fmt ') {
      fmt = {
        audioFormat: buf.readUInt16LE(body),
        channels: buf.readUInt16LE(body + 2),
        sampleRate: buf.readUInt32LE(body + 4),
        bitsPerSample: buf.readUInt16LE(body + 14),
      }
    } else if (id === 'data') {
      data = buf.subarray(body, body + size)
    }
    offset = body + size + (size % 2)
  }
  if (!fmt || !data) throw new Error('WAV 缺少 fmt 或 data 段')
  const bytesPerSample = fmt.bitsPerSample / 8
  const frames = Math.floor(data.length / (bytesPerSample * fmt.channels))
  const samples = new Float32Array(frames)
  for (let i = 0; i < frames; i++) {
    const index = i * bytesPerSample * fmt.channels
    if (fmt.bitsPerSample === 16) {
      samples[i] = data.readInt16LE(index) / 32768
    } else if (fmt.bitsPerSample === 8) {
      samples[i] = (data.readUInt8(index) - 128) / 128
    } else if (fmt.bitsPerSample === 32) {
      samples[i] = data.readInt32LE(index) / 2147483648
    } else {
      throw new Error(`不支持的位深：${fmt.bitsPerSample}`)
    }
  }
  return { sampleRate: fmt.sampleRate, channels: fmt.channels, samples }
}

/** 线性插值重采样 */
function resample(samples, from, to) {
  if (from === to) return samples
  const ratio = from / to
  const length = Math.floor(samples.length / ratio)
  const out = new Float32Array(length)
  for (let i = 0; i < length; i++) {
    const pos = i * ratio
    const left = Math.floor(pos)
    const right = Math.min(samples.length - 1, left + 1)
    const weight = pos - left
    out[i] = samples[left] * (1 - weight) + samples[right] * weight
  }
  return out
}

function toPcm16(samples) {
  const out = Buffer.alloc(samples.length * 2)
  for (let i = 0; i < samples.length; i++) {
    const clamped = Math.max(-1, Math.min(1, samples[i]))
    out.writeInt16LE(Math.round(clamped * 32767), i * 2)
  }
  return out
}

async function login() {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'candidate', password: '123456' }),
  })
  const body = await res.json()
  if (!body?.data?.accessToken) throw new Error(`登录失败：${res.status}`)
  return body.data.accessToken
}

const wavPath = process.argv[2] || generateSpeechWav()
console.log('测试音频：', wavPath)
const { sampleRate, channels, samples } = readWav(wavPath)
const mono = channels === 1 ? samples : resample(samples.filter((_, i) => i % channels === 0), sampleRate, sampleRate)
const pcm = toPcm16(resample(mono, sampleRate, SAMPLE_RATE))
const frameBytes = (SAMPLE_RATE * FRAME_MS) / 1000 * 2
const totalFrames = Math.ceil(pcm.length / frameBytes)
console.log(`原始采样率=${sampleRate} 时长=${(samples.length / sampleRate).toFixed(1)}s 帧数=${totalFrames}`)

const token = await login()
const transcripts = []
const errors = []

const ws = new WebSocket(`${WS}?token=${encodeURIComponent(token)}&language=zh`)
ws.binaryType = 'arraybuffer'

const ready = new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('等待 READY 超时')), 10000)
  ws.onmessage = (event) => {
    const data = JSON.parse(String(event.data))
    if (data.type === 'READY') {
      clearTimeout(timer)
      console.log(`READY sampleRate=${data.sampleRate} frameMs=${data.frameMs} provider=${data.provider}`)
      resolve()
      return
    }
    if (data.type === 'TRANSCRIPT') {
      transcripts.push(data)
      console.log(
        `TRANSCRIPT seq=${data.seq} ${data.startMs}-${data.endMs}ms 转写耗时=${data.latencyMs}ms 文本=${JSON.stringify(data.text)}`,
      )
      return
    }
    if (data.type === 'ERROR') {
      errors.push(data.message)
      console.log('ERROR', data.message)
      return
    }
    if (data.type === 'SPEECH_START') console.log('SPEECH_START')
  }
  ws.onerror = (e) => reject(new Error('WebSocket 连接失败'))
})

await new Promise((resolve, reject) => {
  ws.onopen = resolve
  ws.onerror = () => reject(new Error('WebSocket 握手失败'))
})
await ready

// 按实时节奏推帧（每帧 100ms，略快一点以便测试尽早结束）
for (let i = 0; i < totalFrames; i++) {
  const chunk = pcm.subarray(i * frameBytes, Math.min(pcm.length, (i + 1) * frameBytes))
  ws.send(chunk)
  await new Promise((r) => setTimeout(r, 40))
}
// 补一段静音，触发 VAD 断句
const silence = Buffer.alloc(frameBytes)
for (let i = 0; i < 10; i++) {
  ws.send(silence)
  await new Promise((r) => setTimeout(r, 40))
}
ws.send(JSON.stringify({ type: 'STOP' }))
await new Promise((r) => setTimeout(r, 4000))
ws.close()

const text = transcripts.map((t) => t.text).join('')
const latencies = transcripts.map((t) => t.latencyMs)
console.log('\n=== 结果 ===')
console.log('段数：', transcripts.length)
console.log('合并文本：', text)
console.log('首段延迟：', latencies[0] ?? '-', 'ms')
if (errors.length) console.log('错误：', errors.join(' | '))
console.log(transcripts.length > 0 && text.trim().length > 0 ? '✅ 流式转写通过' : '❌ 流式转写未产生文本')
process.exit(transcripts.length > 0 && text.trim().length > 0 ? 0 : 1)
