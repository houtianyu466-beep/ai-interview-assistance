/**
 * VAD 敏感性诊断：把同一段真实语音按不同音量推给 /ws/asr，
 * 记录每档是否触发 SPEECH_START / TRANSCRIPT，以及音频的 RMS 分布。
 *
 * 用法：node scripts/vad-probe.mjs [wav路径]
 */
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const API = 'http://127.0.0.1:8080/api/v1'
const WS = 'ws://127.0.0.1:8080/ws/asr'
const RATE = 16000
const FRAME_MS = 100
const FRAME_BYTES = (RATE * FRAME_MS) / 1000 * 2
const TEXT = '你好，我是张伟，有五年 Java 后端开发经验，做过高并发交易系统。'

function genWav() {
  const dir = mkdtempSync(join(tmpdir(), 'vad-probe-'))
  const file = join(dir, 'speech.wav')
  const ps = `\ufeffAdd-Type -AssemblyName System.Speech
$sp = New-Object System.Speech.Synthesis.SpeechSynthesizer
$sp.SetOutputToWaveFile('${file.replace(/\\/g, '\\\\')}')
$sp.Speak('${TEXT}')
$sp.Dispose()
`
  writeFileSync(join(dir, 'gen.ps1'), ps, 'utf8')
  execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(dir, 'gen.ps1')], { stdio: 'ignore' })
  return file
}

function readWav(path) {
  const buf = readFileSync(path)
  let offset = 12, fmt = null, data = null
  while (offset + 8 <= buf.length) {
    const id = buf.toString('ascii', offset, offset + 4)
    const size = buf.readUInt32LE(offset + 4)
    const body = offset + 8
    if (id === 'fmt ') fmt = { channels: buf.readUInt16LE(body + 2), rate: buf.readUInt32LE(body + 4), bits: buf.readUInt16LE(body + 14) }
    else if (id === 'data') data = buf.subarray(body, body + size)
    offset = body + size + (size % 2)
  }
  const bytes = fmt.bits / 8
  const frames = Math.floor(data.length / (bytes * fmt.channels))
  const out = new Float32Array(frames)
  for (let i = 0; i < frames; i++) {
    const idx = i * bytes * fmt.channels
    out[i] = fmt.bits === 16 ? data.readInt16LE(idx) / 32768 : data.readInt32LE(idx) / 2147483648
  }
  return { rate: fmt.rate, samples: out }
}

function resample(src, from, to) {
  if (from === to) return src
  const ratio = from / to
  const len = Math.floor(src.length / ratio)
  const out = new Float32Array(len)
  for (let i = 0; i < len; i++) {
    const pos = i * ratio, l = Math.floor(pos), r = Math.min(src.length - 1, l + 1), w = pos - l
    out[i] = src[l] * (1 - w) + src[r] * w
  }
  return out
}

function rms(samples) {
  let sum = 0
  for (const v of samples) sum += v * v
  return Math.sqrt(sum / samples.length)
}

async function login() {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'candidate', password: '123456' }),
  })
  return (await r.json()).data.accessToken
}

const wavPath = process.argv[2] || genWav()
const { rate, samples } = readWav(wavPath)
const mono16 = resample(samples, rate, RATE)

// 统计整段 RMS 与分帧 RMS 分布
console.log('音频:', wavPath, '采样率', rate, '时长', (samples.length / rate).toFixed(1) + 's')
const frameRms = []
for (let i = 0; i + FRAME_BYTES / 2 <= mono16.length; i += FRAME_BYTES / 2) {
  frameRms.push(rms(mono16.subarray(i, i + FRAME_BYTES / 2)))
}
const sorted = [...frameRms].sort((a, b) => a - b)
const pct = (p) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))]
console.log('帧 RMS: 中位数=%s p20=%s p50=%s p80=%s 最大=%s',
  pct(0.5).toFixed(4), pct(0.2).toFixed(4), pct(0.5).toFixed(4), pct(0.8).toFixed(4), sorted.at(-1).toFixed(4))

const token = await login()

async function probe(amplitude, label) {
  const scaled = mono16.map((v) => Math.max(-1, Math.min(1, v * amplitude)))
  const pcm = Buffer.alloc(scaled.length * 2)
  for (let i = 0; i < scaled.length; i++) pcm.writeInt16LE(Math.round(scaled[i] * 32767), i * 2)

  const events = []
  const ws = new WebSocket(`${WS}?token=${encodeURIComponent(token)}&language=zh`)
  ws.binaryType = 'arraybuffer'
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('ws fail')) })
  ws.onmessage = (e) => {
    const d = JSON.parse(String(e.data))
    if (d.type !== 'READY') events.push(d)
  }
  await new Promise((r) => setTimeout(r, 300))
  for (let i = 0; i * FRAME_BYTES < pcm.length; i++) {
    ws.send(pcm.subarray(i * FRAME_BYTES, Math.min(pcm.length, (i + 1) * FRAME_BYTES)))
    await new Promise((r) => setTimeout(r, 25))
  }
  const silence = Buffer.alloc(FRAME_BYTES)
  for (let i = 0; i < 12; i++) { ws.send(silence); await new Promise((r) => setTimeout(r, 25)) }
  ws.send(JSON.stringify({ type: 'STOP' }))
  await new Promise((r) => setTimeout(r, 9000))
  ws.close()

  const starts = events.filter((e) => e.type === 'SPEECH_START').length
  const errorMsgs = events.filter((e) => e.type === 'ERROR').map((e) => e.message)
  if (events.length) console.log('   events:', events.map((e) => e.type + (e.message ? ':' + e.message : e.text ? ':' + JSON.stringify(e.text) : '')).join(', '))
  const transcripts = events.filter((e) => e.type === 'TRANSCRIPT')
  const text = transcripts.map((t) => t.text).join('')
  console.log(
    `${label.padEnd(10)} 增益=${amplitude.toFixed(2)} SPEECH_START=${starts} 段数=${transcripts.length} 文本=${JSON.stringify(text.slice(0, 40))} 延迟=${transcripts[0]?.latencyMs ?? '-'}ms`,
  )
  return { amplitude, starts, transcripts: transcripts.length, text }
}

const results = []
for (const a of [1.0, 0.35, 0.2, 0.06]) {
  results.push(await probe(a, `档位${a}`))
}

console.log('\n=== 结论 ===')
for (const r of results) {
  console.log(`${r.amplitude.toFixed(2)} → ${r.transcripts > 0 ? '识别成功' : '未识别'}`)
}
process.exit(0)
