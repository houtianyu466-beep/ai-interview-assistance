<template>
  <div class="call" :class="isAi ? 'call--ai' : 'call--human'">
    <!-- 兜底：浏览器不支持 WebRTC -->
    <div v-if="bootStage === 'unsupported'" class="call-state">
      <div class="call-state__card">
        <span class="call-state__icon"><el-icon :size="34"><WarningFilled /></el-icon></span>
        <h1>当前浏览器不支持视频通话</h1>
        <p class="call-state__desc">
          视频面试依赖 WebRTC 能力，请使用最新版 Chrome、Edge 或 Safari 打开本页；移动端请使用系统浏览器而不是微信内置浏览器。
        </p>
        <div class="call-state__actions">
          <el-button type="primary" @click="boot">重新检测</el-button>
          <el-button @click="goLobby">返回视频面试</el-button>
        </div>
      </div>
    </div>

    <!-- 兜底：摄像头/麦克风权限失败 -->
    <div v-else-if="bootStage === 'media-error'" class="call-state">
      <div class="call-state__card">
        <span class="call-state__icon call-state__icon--warn"><el-icon :size="34"><VideoPause /></el-icon></span>
        <h1>无法使用摄像头/麦克风</h1>
        <p class="call-state__desc">{{ mediaErrorText || '摄像头或麦克风不可用，请检查设备和浏览器权限。' }}</p>
        <ul class="call-state__tips">
          <li>点击地址栏左侧的锁形图标，把「摄像头」「麦克风」改为允许，然后点「重新授权」。</li>
          <li>关闭正在占用摄像头的其他软件（会议、录屏工具等）。</li>
          <li>确认设备已正确连接，并且没有被系统禁用。</li>
        </ul>
        <div class="call-state__actions">
          <el-button type="primary" @click="boot">重新授权</el-button>
          <el-button v-if="isAi" @click="continueWithoutMedia">仅文字作答继续</el-button>
          <el-button @click="goLobby">返回视频面试</el-button>
        </div>
      </div>
    </div>

    <!-- 兜底：房间不存在 / 已结束 / 接口失败 -->
    <div v-else-if="bootStage === 'error'" class="call-state">
      <div class="call-state__card">
        <span class="call-state__icon call-state__icon--warn"><el-icon :size="34"><Warning /></el-icon></span>
        <h1>进入视频通话失败</h1>
        <p class="call-state__desc">{{ bootError || '房间不存在或已结束，请重新创建房间。' }}</p>
        <div class="call-state__actions">
          <el-button type="primary" @click="boot">重试</el-button>
          <el-button @click="goLobby">返回视频面试</el-button>
        </div>
      </div>
    </div>

    <!-- 初始化中 -->
    <div v-else-if="bootStage === 'loading'" class="call-state call-state--loading">
      <div class="call-state__card">
        <el-icon class="call-state__spinner" :size="30"><Loading /></el-icon>
        <h1>正在进入视频通话…</h1>
        <p class="call-state__desc">正在校验房间、获取 ICE 配置并请求摄像头/麦克风权限，请允许浏览器弹窗。</p>
      </div>
    </div>

    <!-- 通话主界面 -->
    <template v-else>
      <!-- 画面区 -->
      <div v-if="!isAi" class="call__stage">
        <video ref="remoteVideoEl" class="call__remote" autoplay playsinline />
        <div v-if="!hasRemoteStream" class="call__waiting">
          <div class="call__waiting-avatar">
            <el-icon :size="34"><UserFilled /></el-icon>
          </div>
          <h2>{{ waitingTitle }}</h2>
          <p class="call__waiting-hint">{{ waitingHint }}</p>

          <div v-if="peers.length === 0 && !peerHangup" class="call__invite">
            <span class="call__invite-label">房间码</span>
            <strong class="call__invite-code">{{ roomCode }}</strong>
            <div class="call__invite-actions">
              <el-button size="small" :icon="CopyDocument" @click="copyRoomCode">复制房间码</el-button>
              <el-button size="small" :icon="Link" @click="copyInviteLink">复制加入链接</el-button>
            </div>
          </div>

          <div class="call__waiting-actions">
            <el-button v-if="peerHangup" type="primary" @click="goLobby">返回视频面试</el-button>
            <el-button v-else-if="peers.length > 0" :icon="Refresh" @click="reconnectSignaling">
              重新连接
            </el-button>
          </div>

          <p v-if="iceWarning" class="call__ice-tip">{{ iceWarning }}</p>
        </div>

        <button v-if="needsAudioUnlock" type="button" class="call__audio-unlock" @click="unlockAudio">
          <el-icon><Headset /></el-icon>
          点击开启对方声音
        </button>
      </div>

      <div v-else class="call__stage call__stage--ai">
        <div class="ai-stage">
          <div class="ai-stage__avatar" :class="{ 'is-speaking': speaking, 'is-live': socketConnected }">
            <span class="ai-stage__halo" aria-hidden="true" />
            <el-icon :size="40"><Cpu /></el-icon>
          </div>
          <h2 class="ai-stage__name">AI 面试官</h2>
          <p class="ai-stage__state">{{ aiStageState }}</p>

          <div v-if="aiQuestion" class="ai-stage__card">
            <div class="ai-stage__card-head">
              <span class="ai-stage__seq">{{ aiProgress || '当前题目' }}</span>
              <span class="ai-stage__qtime">
                <el-icon><Timer /></el-icon>
                {{ questionClock }}
              </span>
            </div>
            <p class="ai-stage__question">{{ aiQuestion }}</p>
          </div>

          <p v-if="aiFinished" class="ai-stage__finished">
            <el-icon><CircleCheck /></el-icon>
            本轮提问已结束，可以前往复盘
          </p>
        </div>
      </div>

      <!-- 顶部信息条 -->
      <header class="call__top">
        <div class="call__top-main">
          <div class="call__code">
            房间 <span>{{ roomCode }}</span>
          </div>
          <div class="call__meta">
            <span class="call__badge" :class="isAi ? 'call__badge--ai' : 'call__badge--human'">
              {{ isAi ? 'AI 模拟面试' : '真人 1v1' }}
            </span>
            <span class="call__timer">{{ callClock }}</span>
            <span class="call__status" :class="statusTone">
              <span class="call__dot" aria-hidden="true" />
              {{ connectionLabel }}
            </span>
          </div>
        </div>

        <div class="call__top-actions">
          <template v-if="isAi && ttsSupported">
            <el-button text class="call__action" @click="toggleTts">
              {{ ttsEnabled ? '静音 AI 语音' : '开启 AI 语音' }}
            </el-button>
            <el-button v-if="ttsEnabled" text class="call__action" @click="replayQuestion">重听</el-button>
          </template>
          <el-button v-if="!isAi" text class="call__action" :icon="Link" @click="copyInviteLink">
            邀请
          </el-button>
          <el-button
            text
            class="call__action call__action--close"
            :icon="Close"
            aria-label="离开通话"
            @click="confirmLeave"
          />
        </div>
      </header>

      <el-alert
        v-if="socketNotice"
        class="call__banner"
        :type="socketNoticeType"
        :closable="false"
        show-icon
        :title="socketNotice"
      />

      <!-- 真人模式：基础聊天消息展示 -->
      <div v-if="!isAi && chatLines.length > 0" class="call__chat" aria-live="polite">
        <p v-for="(line, index) in chatLines" :key="`${index}-${line.name}`" class="call__chat-line">
          <strong>{{ line.name }}：</strong>{{ line.text }}
        </p>
      </div>

      <!-- AI 作答面板 -->
      <aside v-if="isAi" class="call__dock">
        <header class="dock__head">
          <div>
            <p class="dock__eyebrow">我的作答</p>
            <p class="dock__sub">{{ aiProgress || '等待 AI 出题' }}</p>
          </div>
          <el-tag v-if="recording" type="danger" effect="dark" size="small" round>录音中</el-tag>
          <el-tag v-else-if="transcribing" type="warning" effect="dark" size="small" round>转写中</el-tag>
          <el-tag v-else-if="answerResult" type="success" effect="dark" size="small" round>已提交</el-tag>
        </header>

        <div v-if="aiFinished" class="dock__finished">
          <span class="dock__finished-icon"><el-icon :size="26"><Trophy /></el-icon></span>
          <h3>面试结束</h3>
          <p>AI 已完成本轮提问，去复盘看看整体表现、逐题参考答案和下一步建议。</p>
          <el-button type="primary" class="w-full" :disabled="!practiceSessionId" @click="goReview">
            查看复盘
          </el-button>
          <el-button text class="w-full" @click="confirmLeave">离开通话</el-button>
        </div>

        <template v-else-if="answerResult">
          <div class="dock__score">
            <span class="dock__score-value">
              {{ formatScore(answerResult.score) }}<small>分</small>
            </span>
            <el-tag :type="feedbackTagType" effect="light" round>{{ feedbackTagLabel }}</el-tag>
          </div>
          <p v-if="answerResult.comment" class="dock__comment">{{ answerResult.comment }}</p>
          <div v-if="answerResult.hits?.length" class="dock__tags">
            <el-tag
              v-for="hit in answerResult.hits"
              :key="`hit-${hit}`"
              size="small"
              type="success"
              effect="light"
            >
              {{ hit }}
            </el-tag>
          </div>
          <div v-if="answerResult.misses?.length" class="dock__tags">
            <el-tag
              v-for="miss in answerResult.misses"
              :key="`miss-${miss}`"
              size="small"
              type="danger"
              effect="light"
            >
              {{ miss }}
            </el-tag>
          </div>
          <p v-if="submittedAnswer" class="dock__answer">{{ submittedAnswer }}</p>
          <p class="dock__next-hint">
            {{ answerResult.finished ? '本轮题目已完成，可以查看复盘。' : 'AI 正在准备下一题…' }}
          </p>
          <div class="dock__actions">
            <el-button v-if="answerResult.finished" type="primary" class="w-full" @click="goReview">
              查看复盘
            </el-button>
            <el-button
              v-else
              type="primary"
              class="w-full"
              :loading="awaitingNext"
              :disabled="!socketConnected"
              @click="requestNext"
            >
              下一题
            </el-button>
          </div>
        </template>

        <template v-else>
          <div class="dock__record">
            <el-button
              v-if="recording"
              type="danger"
              :icon="VideoPause"
              @click="stopRecording"
            >
              停止录音并转写（{{ voiceClock }}）
            </el-button>
            <el-button
              v-else
              type="primary"
              plain
              :icon="Microphone"
              :loading="transcribing"
              :disabled="transcribing || submitting || (!voiceSupported && !streamSupported)"
              @click="startRecording"
            >
              {{ transcribing ? '正在转写…' : '语音作答' }}
            </el-button>
            <span class="dock__record-hint">
              <template v-if="recording && streamingMode">
                正在实时转写，说完一句就会出字
              </template>
              <template v-else-if="recording">正在录音，请开始作答</template>
              <template v-else-if="streamError">{{ streamError }}</template>
              <template v-else-if="voiceError">{{ voiceError }}</template>
              <template v-else>说完一句立即转写，可修改后再提交</template>
            </span>
          </div>

          <!-- 流式转写实时字幕：说完一句就显示一句 -->
          <div v-if="streamingMode && (recording || streamText)" class="dock__live">
            <span class="dock__live-dot" :class="{ 'is-active': streamSpeechActive }" />
            <p class="dock__live-text">{{ streamText || '正在聆听…' }}</p>
            <span v-if="streamLatency > 0" class="dock__live-meta">{{ streamLatency }}ms</span>
          </div>

          <el-alert
            v-if="asrUnavailable"
            class="dock__asr"
            type="warning"
            :closable="false"
            show-icon
            title="语音转写暂不可用，请直接用文字作答"
          />

          <el-input
            v-model="answerText"
            class="dock__input"
            type="textarea"
            :rows="5"
            maxlength="10000"
            show-word-limit
            :disabled="submitting || recording"
            placeholder="输入或语音转写你的回答，先给结论再分点展开…"
            @keydown="onAnswerKeydown"
          />

          <div class="dock__foot">
            <span class="dock__foot-hint">本题用时 {{ questionClock }} · 提交后不可修改</span>
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="!canSubmit"
              @click="submitAnswer"
            >
              提交回答
            </el-button>
          </div>

          <p v-if="!aiQuestion" class="dock__waiting">AI 正在出题，也可以先写下你的思路。</p>
        </template>
      </aside>

      <!-- 本地画中画 -->
      <div class="call__pip" :class="{ 'is-off': !cameraEnabled || !localStream }">
        <video ref="localVideoEl" class="call__pip-video" autoplay playsinline muted />
        <div v-if="!cameraEnabled || !localStream" class="call__pip-placeholder">
          <el-icon :size="20"><VideoPause /></el-icon>
          <span>{{ localStream ? '摄像头已关闭' : '无本地画面' }}</span>
        </div>
        <span class="call__pip-name">{{ selfName }}</span>
      </div>

      <!-- 底部控制条 -->
      <footer class="call__controls">
        <button
          type="button"
          class="ctrl"
          :class="{ 'is-off': !micEnabled }"
          :aria-pressed="micEnabled"
          @click="toggleMic"
        >
          <span class="ctrl__btn">
            <el-icon :size="22"><Microphone v-if="micEnabled" /><Mute v-else /></el-icon>
          </span>
          <span class="ctrl__label">{{ micEnabled ? '麦克风' : '已静音' }}</span>
        </button>

        <button
          type="button"
          class="ctrl"
          :class="{ 'is-off': !cameraEnabled }"
          :aria-pressed="cameraEnabled"
          @click="toggleCamera"
        >
          <span class="ctrl__btn">
            <el-icon :size="22"><VideoCamera /></el-icon>
          </span>
          <span class="ctrl__label">{{ cameraEnabled ? '摄像头' : '已关闭' }}</span>
        </button>

        <button type="button" class="ctrl ctrl--hangup" @click="confirmLeave">
          <span class="ctrl__btn">
            <el-icon :size="24"><PhoneFilled /></el-icon>
          </span>
          <span class="ctrl__label">挂断</span>
        </button>
      </footer>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck,
  Close,
  CopyDocument,
  Cpu,
  Headset,
  Link,
  Loading,
  Microphone,
  Mute,
  PhoneFilled,
  Refresh,
  Timer,
  Trophy,
  UserFilled,
  VideoCamera,
  VideoPause,
  Warning,
  WarningFilled,
} from '@element-plus/icons-vue'
import { fetchCurrentQuestion, submitPracticeAnswer } from '@/api/practice'
import { transcribeAudio } from '@/api/voice'
import { endVideoRoom, fetchIceConfig, fetchVideoRoom } from '@/api/video'
import { ApiError } from '@/api/http'
import { useCallSocket } from '@/composables/useCallSocket'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import { useStreamingTranscription } from '@/composables/useStreamingTranscription'
import {
  createPeer,
  isGetUserMediaSupported,
  isWebRTCSupported,
  type WebRTCPeer,
} from '@/composables/useWebRTC'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'
import { formatScore, scoreLevel } from '@/utils/format'
import type { AnswerResultVO } from '@/types/api'
import type { PracticeAnswerRequest } from '@/types/practice'
import type {
  AiFinishedData,
  AiSayData,
  CallErrorData,
  CallMessage,
  CallMode,
  CallPeer,
  CallSocketStatus,
  ChatData,
  IceCandidateData,
  IceServer,
  PeerEventData,
  RoomStateData,
  SessionDescriptionData,
} from '@/types/video'

type BootStage = 'loading' | 'ready' | 'error' | 'unsupported' | 'media-error'

/** 后端没配置 TURN 时的保底 STUN（只在 /v1/video/ice 失败时使用） */
const DEFAULT_ICE_SERVERS: IceServer[] = [{ urls: ['stun:stun.l.google.com:19302'] }]
/** 这些状态表示房间已结束，不再允许进入 */
const ENDED_STATUSES = ['ENDED', 'CLOSED', 'FINISHED', 'CANCELLED']
/** 加入者兜底：ROOM_STATE 后房主迟迟没发起时，polite 一方主动 offer（perfect negotiation 会化解冲突） */
const FALLBACK_OFFER_DELAY_MS = 4_000
/** 点「下一题」后等待服务端推题的超时时间 */
const NEXT_QUESTION_TIMEOUT_MS = 20_000

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roomCode = computed(() => {
  const raw = route.params.code
  const value = Array.isArray(raw) ? raw[0] : raw
  return String(value ?? '').trim()
})

const bootStage = ref<BootStage>('loading')
const bootError = ref('')
const mediaErrorText = ref('')

const mode = ref<CallMode>('HUMAN')
const isAi = computed(() => mode.value === 'AI')

const localStream = ref<MediaStream | null>(null)
const remoteStream = ref<MediaStream | null>(null)
const localVideoEl = ref<HTMLVideoElement | null>(null)
const remoteVideoEl = ref<HTMLVideoElement | null>(null)
const micEnabled = ref(true)
const cameraEnabled = ref(true)
const needsAudioUnlock = ref(false)

const iceServers = ref<IceServer[]>([])
const iceWarning = ref('')

const selfId = ref<string | number>('')
const selfUserId = ref<string | number>('')
const hostUserId = ref<string | number>('')
const peers = ref<CallPeer[]>([])
const practiceSessionId = ref('')
const peerHangup = ref(false)
const connectionHint = ref('')
const chatLines = ref<{ name: string; text: string }[]>([])

const socketNotice = ref('')
const socketNoticeType = ref<'warning' | 'error'>('warning')

const elapsedSeconds = ref(0)
let callStartedAt = Date.now()
let tickerId: number | null = null

const peerState = ref<RTCPeerConnectionState | 'idle'>('idle')

// AI 面试状态
const aiQuestion = ref('')
const aiQuestionId = ref('')
const aiSeq = ref(0)
const aiTotal = ref(0)
const aiFinished = ref(false)
const questionElapsed = ref(0)
const answerText = ref('')
const submittedAnswer = ref('')
const audioPath = ref('')
const answerResult = ref<AnswerResultVO | null>(null)
const submitting = ref(false)
const transcribing = ref(false)
/** 流式录音的计时器（批式计时由 useVoiceRecorder 提供） */
const streamElapsed = ref(0)
let streamTimer: number | null = null
const asrUnavailable = ref(false)
const awaitingNext = ref(false)

const ttsSupported = typeof window !== 'undefined' && 'speechSynthesis' in window
const ttsEnabled = ref(true)
const speaking = ref(false)

const {
  status: socketStatus,
  connected: socketConnected,
  reconnectAttempt,
  lastError: socketLastError,
  connect: connectSocket,
  send: sendCallMessage,
  close: closeSocket,
} = useCallSocket()

const {
  supported: voiceSupported,
  recording: batchRecording,
  elapsedSeconds: voiceElapsed,
  error: voiceError,
  start: startVoiceRecorder,
  stop: stopVoiceRecorder,
  cancel: cancelVoiceRecorder,
} = useVoiceRecorder()

// 流式转写：说完一句就能出字，不必等整段录音上传
const {
  supported: streamSupported,
  recording: streamRecording,
  speechActive: streamSpeechActive,
  text: streamText,
  lastLatencyMs: streamLatency,
  error: streamError,
  start: streamStart,
  stop: streamStop,
  reset: streamReset,
} = useStreamingTranscription()

/** 本次录音是否走流式通道 */
const streamingMode = ref(false)
/** 录音中：批式与流式任一进行中都算 */
const recording = computed(() => batchRecording.value || streamRecording.value)

let peer: WebRTCPeer | null = null
let initializing = false
let leaving = false
let alive = true
let bootToken = 0
let everConnected = false
let receivedOffer = false
let offering = false
let questionStartMs = 0
let fallbackOfferTimer: number | null = null
let nextQuestionTimer: number | null = null
let previousBodyOverflow = ''
const deferredOffers: RTCSessionDescriptionInit[] = []
const deferredCandidates: (RTCIceCandidateInit | null)[] = []

const isHost = computed(() => idsEqual(selfUserId.value, hostUserId.value))
const selfName = computed(() => auth.displayName)

const callClock = computed(() => formatClock(elapsedSeconds.value))
const questionClock = computed(() => formatClock(questionElapsed.value))
const voiceClock = computed(() =>
  formatClock(streamingMode.value ? streamElapsed.value : voiceElapsed.value),
)
const aiProgress = computed(() =>
  aiSeq.value > 0 && aiTotal.value > 0 ? `第 ${aiSeq.value} / ${aiTotal.value} 题` : '',
)

const inviteLink = computed(() =>
  roomCode.value ? `${window.location.origin}/call/${encodeURIComponent(roomCode.value)}` : '',
)

const hasRemoteStream = computed(() => Boolean(remoteStream.value))
const canSubmit = computed(
  () =>
    Boolean(answerText.value.trim()) &&
    !submitting.value &&
    !recording.value &&
    !transcribing.value &&
    !aiFinished.value,
)
const feedbackLevel = computed(() => scoreLevel(answerResult.value?.score))
const feedbackTagType = computed<'success' | 'warning' | 'danger'>(() => {
  if (feedbackLevel.value === 'high') return 'success'
  if (feedbackLevel.value === 'middle') return 'warning'
  return 'danger'
})
const feedbackTagLabel = computed(() => {
  if (feedbackLevel.value === 'high') return '表现出色'
  if (feedbackLevel.value === 'middle') return '基本合格'
  return '还需加强'
})

const statusTone = computed(() => {
  if (!socketConnected.value) {
    return socketStatus.value === 'failed' ? 'is-error' : 'is-warn'
  }
  if (mode.value === 'HUMAN' && peerState.value === 'failed') return 'is-error'
  if (mode.value === 'HUMAN' && !hasRemoteStream.value) return 'is-warn'
  return 'is-ok'
})

const connectionLabel = computed(() => {
  if (mode.value === 'AI') {
    if (socketConnected.value) return 'AI 在线'
    if (socketStatus.value === 'reconnecting') return `重连中 ${reconnectAttempt.value}/5`
    if (socketStatus.value === 'failed') return '信令连接失败'
    return '连接中…'
  }
  if (!socketConnected.value) {
    if (socketStatus.value === 'reconnecting') return `重连中 ${reconnectAttempt.value}/5`
    if (socketStatus.value === 'failed') return '信令连接失败'
    return '连接中…'
  }
  if (peerState.value === 'failed') return '视频连接失败'
  if (peerState.value === 'disconnected') return '连接不稳定'
  if (hasRemoteStream.value) return '通话中'
  return peers.value.length > 0 ? '正在连接对方…' : '等待对方加入'
})

const waitingTitle = computed(() => {
  if (peerHangup.value) return '对方已结束通话'
  if (peers.value.length === 0) return '等待对方加入'
  if (peerState.value === 'failed') return '视频连接失败'
  if (!socketConnected.value) return '信令连接中…'
  return '正在连接对方…'
})

const waitingHint = computed(() => {
  if (peerHangup.value) return '可以返回视频面试，重新邀请朋友或加入新的房间。'
  if (peers.value.length === 0) {
    return '把房间码或加入链接发给朋友，对方进入房间后会自动建立视频连接。'
  }
  if (peerState.value === 'failed') {
    return '请检查双方网络；如果一直失败，可能是服务端未配置 TURN 服务。'
  }
  if (!socketConnected.value) {
    return '正在重连信令服务，恢复后会自动继续建立连接。'
  }
  return connectionHint.value || '正在交换音视频信息，请稍候…'
})

const aiStageState = computed(() => {
  if (aiFinished.value) return '面试已结束'
  if (!socketConnected.value) return '信令重连中，请稍候…'
  if (answerResult.value) return '已收到你的回答，正在准备下一题'
  if (recording.value) return '正在听你作答…'
  if (aiQuestion.value) return '请开始作答'
  return '准备开始'
})

function idsEqual(left: string | number | null | undefined, right: string | number | null | undefined): boolean {
  if (left == null || right == null || left === '' || right === '') return false
  return String(left) === String(right)
}

function isEndedStatus(status: unknown): boolean {
  return ENDED_STATUSES.includes(String(status ?? '').toUpperCase())
}

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds || 0))
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const seconds = safe % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}

function stopMediaStream(stream: MediaStream | null): void {
  if (!stream) return
  stream.getTracks().forEach((track) => {
    try {
      track.stop()
    } catch {
      // 轨道已经结束时忽略
    }
  })
}

function describeMediaError(error: unknown): string {
  const name = error instanceof DOMException ? error.name : (error as { name?: string } | null)?.name
  switch (name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
    case 'SecurityError':
      return '摄像头/麦克风权限被拒绝。请在浏览器地址栏允许权限后点「重新授权」。'
    case 'NotFoundError':
    case 'DevicesNotFoundError':
    case 'OverconstrainedError':
      return '未检测到可用的摄像头或麦克风，请连接设备后重试。'
    case 'NotReadableError':
    case 'TrackStartError':
    case 'AbortError':
      return '摄像头或麦克风被其他程序占用，请关闭占用程序后重试。'
    default:
      return '无法打开摄像头/麦克风，请检查设备与浏览器权限设置后重试。'
  }
}

function normalizeIceServers(list: IceServer[] | null | undefined): IceServer[] {
  if (!Array.isArray(list)) return []
  return list.filter((server) => {
    if (!server || server.urls == null) return false
    return Array.isArray(server.urls) ? server.urls.length > 0 : Boolean(String(server.urls).trim())
  })
}

async function loadIceServers(): Promise<void> {
  iceWarning.value = ''
  try {
    const config = await fetchIceConfig()
    const list = normalizeIceServers(config?.iceServers)
    if (list.length > 0) {
      iceServers.value = list
      return
    }
    iceServers.value = DEFAULT_ICE_SERVERS
    iceWarning.value = '服务端未返回 ICE 配置，已使用默认 STUN，跨网络连接可能不稳定。'
  } catch {
    iceServers.value = DEFAULT_ICE_SERVERS
    iceWarning.value = '获取 ICE 配置失败，已使用默认 STUN，跨网络连接可能不稳定。'
  }
}

async function openLocalMedia(): Promise<boolean> {
  if (!isGetUserMediaSupported()) {
    mediaErrorText.value = '当前浏览器不支持摄像头/麦克风采集，请更换 Chrome、Edge 或 Safari 后重试。'
    return false
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' },
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    })
    localStream.value = stream
    const audioTracks = stream.getAudioTracks()
    const videoTracks = stream.getVideoTracks()
    micEnabled.value = audioTracks.length > 0 && audioTracks.every((track) => track.enabled)
    cameraEnabled.value = videoTracks.length > 0
    return true
  } catch (error) {
    mediaErrorText.value = describeMediaError(error)
    return false
  }
}

function bindLocalVideo(): void {
  const element = localVideoEl.value
  if (!element || !localStream.value) return
  if (element.srcObject !== localStream.value) {
    element.srcObject = localStream.value
  }
  void element.play().catch(() => {
    // 本地预览是静音的，autoplay 一般不会被拦截；失败时保持黑屏即可
  })
}

function detachRemoteVideo(): void {
  const element = remoteVideoEl.value
  if (element) element.srcObject = null
}

function attachRemoteStream(stream: MediaStream): void {
  remoteStream.value = stream
  void nextTick(() => {
    const element = remoteVideoEl.value
    if (!element) return
    if (element.srcObject !== stream) element.srcObject = stream
    element.muted = false
    void element
      .play()
      .then(() => {
        needsAudioUnlock.value = false
      })
      .catch(() => {
        // 带声音自动播放被拦截：先静音播放保证画面可见，再提示用户点一下开启声音
        element.muted = true
        void element.play().catch(() => {
          // 静音仍无法播放时保持等待层，用户可手动点击
        })
        needsAudioUnlock.value = true
      })
  })
}

function unlockAudio(): void {
  const element = remoteVideoEl.value
  if (!element) return
  element.muted = false
  void element
    .play()
    .then(() => {
      needsAudioUnlock.value = false
    })
    .catch(() => {
      ElMessage.warning('仍然无法播放声音，请检查系统音量或换用 Chrome')
    })
}

function setLocalTracksEnabled(kind: 'audio' | 'video', enabled: boolean): boolean {
  const tracks = localStream.value?.getTracks().filter((track) => track.kind === kind) ?? []
  tracks.forEach((track) => {
    track.enabled = enabled
  })
  return tracks.length > 0
}

function toggleMic(): void {
  const next = !micEnabled.value
  const applied = peer ? peer.toggleMic(next) : setLocalTracksEnabled('audio', next)
  if (!applied) {
    ElMessage.warning(localStream.value ? '没有可用的麦克风轨道' : '没有麦克风权限，无法切换静音状态')
    return
  }
  micEnabled.value = next
}

function toggleCamera(): void {
  const next = !cameraEnabled.value
  const applied = peer ? peer.toggleCamera(next) : setLocalTracksEnabled('video', next)
  if (!applied) {
    ElMessage.warning(localStream.value ? '没有可用的摄像头轨道' : '没有摄像头权限，无法切换画面')
    return
  }
  cameraEnabled.value = next
  if (next) void nextTick(bindLocalVideo)
}

function ensurePeer(): boolean {
  if (peer) return true
  if (mode.value !== 'HUMAN') return false
  if (!isWebRTCSupported()) {
    bootStage.value = 'unsupported'
    return false
  }
  if (!localStream.value) {
    connectionHint.value = '没有本地音视频轨道，暂时无法建立视频连接'
    return false
  }
  peer = createPeer(iceServers.value, localStream.value, {
    // 房主 impolite，加入者 polite
    polite: !isHost.value,
    onRemoteStream: (stream) => {
      attachRemoteStream(stream)
    },
    onConnectionState: (state) => {
      peerState.value = state
      if (state === 'connected') {
        peerHangup.value = false
        connectionHint.value = ''
      } else if (state === 'connecting') {
        connectionHint.value = '正在建立视频连接…'
      } else if (state === 'disconnected') {
        connectionHint.value = '网络不稳定，连接可能中断…'
      } else if (state === 'failed') {
        connectionHint.value = '视频连接失败，请尝试切换网络或重新进入房间'
      }
    },
    onIceCandidate: (candidate) => {
      sendCallMessage('ICE', { candidate })
    },
    onError: (text) => {
      ElMessage.warning(text)
    },
  })
  return true
}

function resetPeer(): void {
  if (peer) {
    peer.close()
    peer = null
  }
  peerState.value = 'idle'
}

async function startOffer(): Promise<void> {
  if (mode.value !== 'HUMAN' || offering) return
  if (!socketConnected.value) {
    connectionHint.value = '信令尚未连接，恢复后会自动建立视频连接'
    return
  }
  if (!localStream.value) {
    connectionHint.value = '没有本地音视频轨道，暂时无法建立视频连接'
    return
  }
  // 已经连上 / 已有本地 offer 时不重复发起
  if (peer && peer.signalingState() !== 'stable') return
  if (peer && peerState.value === 'connected') return

  offering = true
  try {
    if (!ensurePeer()) return
    const sdp = await peer?.createOffer()
    if (!sdp) {
      connectionHint.value = '发起视频连接失败，请稍后重试'
      return
    }
    if (!sendCallMessage('OFFER', { sdp })) {
      connectionHint.value = '信令已断开，连接请求未发出，正在等待重连'
      return
    }
    connectionHint.value = '已发起视频连接，等待对方接听…'
  } finally {
    offering = false
  }
}

function clearFallbackOffer(): void {
  if (fallbackOfferTimer !== null) {
    window.clearTimeout(fallbackOfferTimer)
    fallbackOfferTimer = null
  }
}

function clearNextQuestionTimer(): void {
  if (nextQuestionTimer !== null) {
    window.clearTimeout(nextQuestionTimer)
    nextQuestionTimer = null
  }
}

function scheduleFallbackOffer(): void {
  clearFallbackOffer()
  if (receivedOffer) return
  fallbackOfferTimer = window.setTimeout(() => {
    fallbackOfferTimer = null
    if (receivedOffer || leaving || mode.value !== 'HUMAN' || !socketConnected.value) return
    if (peer && peer.signalingState() !== 'stable') return
    if (peerState.value === 'connected') return
    void startOffer()
  }, FALLBACK_OFFER_DELAY_MS)
}

function flushDeferredSignals(): void {
  if (mode.value !== 'HUMAN') return
  const offers = deferredOffers.splice(0)
  offers.forEach((sdp) => {
    void handleRemoteOffer({ sdp })
  })
  const candidates = deferredCandidates.splice(0)
  candidates.forEach((candidate) => {
    void handleRemoteIce({ candidate })
  })
}

function handleRoomState(data: RoomStateData): void {
  if (!data || typeof data !== 'object') return
  if (data.selfId != null) selfId.value = data.selfId
  if (data.selfUserId != null) selfUserId.value = data.selfUserId
  if (data.hostUserId != null) hostUserId.value = data.hostUserId
  if (Array.isArray(data.peers)) {
    // 后端字段为 peerId；同时兼容旧契约的 id。ROOM_STATE 的 peers 含自己，UI 只展示对方。
    peers.value = data.peers
      .filter((item) => item && (item.peerId != null || item.id != null))
      .map((item) => ({
        id: String(item.peerId ?? item.id),
        name: item.name || '对方',
      }))
      .filter((item) => !idsEqual(item.id, data.selfId))
  }
  if (data.practiceSessionId) practiceSessionId.value = String(data.practiceSessionId)
  if (data.mode === 'AI' || data.mode === 'HUMAN') mode.value = data.mode

  if (mode.value !== 'HUMAN') return

  flushDeferredSignals()

  if (peers.value.length === 0) {
    peerHangup.value = false
    connectionHint.value = ''
    return
  }
  peerHangup.value = false
  if (isHost.value) {
    void startOffer()
  } else {
    scheduleFallbackOffer()
  }
}

function handlePeerJoined(data: PeerEventData): void {
  const peerId = data?.peerId ?? data?.id
  if (peerId == null) return
  const exists = peers.value.some((item) => idsEqual(item.id, peerId))
  if (!exists) {
    peers.value = [...peers.value, { id: String(peerId), name: data.name || '对方' }]
  }
  if (!peer) receivedOffer = false
  peerHangup.value = false
  if (mode.value !== 'HUMAN') return
  if (isHost.value) void startOffer()
}

function handlePeerLeft(data: PeerEventData): void {
  const peerId = data?.peerId ?? data?.id
  if (peerId == null) return
  peers.value = peers.value.filter((item) => !idsEqual(item.id, peerId))
  resetPeer()
  remoteStream.value = null
  detachRemoteVideo()
  receivedOffer = false
  connectionHint.value = ''
}

async function handleRemoteOffer(data: SessionDescriptionData | undefined): Promise<void> {
  if (mode.value !== 'HUMAN' || !data?.sdp || data.sdp.type !== 'offer') return
  if (selfId.value === '') {
    // ROOM_STATE 还没到，先暂存，等确认角色后再处理
    deferredOffers.push(data.sdp)
    return
  }
  receivedOffer = true
  clearFallbackOffer()
  if (!ensurePeer()) return
  const answer = await peer?.handleOffer(data.sdp)
  if (!answer) {
    connectionHint.value = '对方的连接请求冲突或已过期，正在等待重试'
    return
  }
  if (!sendCallMessage('ANSWER', { sdp: answer })) {
    connectionHint.value = '信令已断开，应答未发出，等待重连后重试'
  }
}

async function handleRemoteAnswer(data: SessionDescriptionData | undefined): Promise<void> {
  if (mode.value !== 'HUMAN' || !data?.sdp || data.sdp.type !== 'answer') return
  if (!peer) return
  await peer.handleAnswer(data.sdp)
}

async function handleRemoteIce(data: IceCandidateData | undefined): Promise<void> {
  if (mode.value !== 'HUMAN' || !data) return
  if (selfId.value === '') {
    deferredCandidates.push(data.candidate ?? null)
    return
  }
  if (!ensurePeer()) return
  await peer?.handleIce(data.candidate)
}

function handleAiSay(data: AiSayData): void {
  if (mode.value !== 'AI' || !data) return
  const text = typeof data.text === 'string' ? data.text.trim() : ''
  if (!text) return

  aiQuestion.value = text
  aiQuestionId.value = data.questionId ? String(data.questionId) : ''
  const seq = Number(data.seq)
  const total = Number(data.totalQuestions)
  aiSeq.value = Number.isFinite(seq) && seq > 0 ? seq : 0
  if (Number.isFinite(total) && total > 0) aiTotal.value = total
  aiFinished.value = false
  answerResult.value = null
  answerText.value = ''
  submittedAnswer.value = ''
  audioPath.value = ''
  awaitingNext.value = false
  clearNextQuestionTimer()
  questionStartMs = Date.now()
  questionElapsed.value = 0
  speak(text)
}

function handleAiFinished(data: AiFinishedData | undefined): void {
  aiFinished.value = true
  awaitingNext.value = false
  clearNextQuestionTimer()
  if (data?.practiceSessionId) practiceSessionId.value = String(data.practiceSessionId)
  cancelSpeech()
}

function handleChat(data: ChatData): void {
  if (!data?.text) return
  const line = {
    name: data.fromName ? String(data.fromName) : '对方',
    text: String(data.text).slice(0, 300),
  }
  chatLines.value = [...chatLines.value, line].slice(-4)
}

function handleServerError(data: CallErrorData | undefined): void {
  let text = ''
  if (typeof data === 'string') {
    text = data
  } else if (data && typeof data === 'object' && data.message) {
    text = String(data.message)
  }
  if (!text) text = '视频服务返回了一个错误，请稍后重试'
  ElMessage.error(text)
}

function handlePeerHangup(): void {
  peerHangup.value = true
  resetPeer()
  remoteStream.value = null
  detachRemoteVideo()
  cancelSpeech()
}

function handleSocketStatus(status: CallSocketStatus, detail?: { attempt?: number }): void {
  if (status === 'reconnecting') {
    socketNoticeType.value = 'warning'
    socketNotice.value = `信令连接已断开，正在自动重连（第 ${detail?.attempt ?? reconnectAttempt.value} / 5 次）…`
  } else if (status === 'failed') {
    socketNoticeType.value = 'error'
    socketNotice.value = socketLastError.value || '信令连接失败，请检查网络后点击「重新连接」'
  } else if (status === 'open') {
    socketNotice.value = ''
  } else if (status === 'connecting' || status === 'idle') {
    socketNotice.value = ''
  } else if (status === 'closed' && !leaving) {
    socketNoticeType.value = 'warning'
    socketNotice.value = '信令连接已关闭'
  } else {
    socketNotice.value = ''
  }
}

function handleSocketOpen(): void {
  if (!everConnected) {
    everConnected = true
    return
  }
  // 重连成功：旧 PeerConnection 已随断线失效，先释放，等 ROOM_STATE / 对方 offer 触发重新协商
  resetPeer()
  remoteStream.value = null
  detachRemoteVideo()
  receivedOffer = false
  peerHangup.value = false
  connectionHint.value = '信令已重连，正在重新建立视频连接…'
}

function connectSignaling(): void {
  connectSocket({
    token: auth.token || getToken(),
    code: roomCode.value,
    name: auth.displayName,
    onMessage: handleCallMessage,
    onStatus: handleSocketStatus,
    onOpen: handleSocketOpen,
  })
}

function handleCallMessage(message: CallMessage): void {
  switch (message.type) {
    case 'ROOM_STATE':
      handleRoomState((message.data ?? {}) as RoomStateData)
      break
    case 'PEER_JOINED':
      handlePeerJoined((message.data ?? {}) as PeerEventData)
      break
    case 'PEER_LEFT':
      handlePeerLeft((message.data ?? {}) as PeerEventData)
      break
    case 'OFFER':
      void handleRemoteOffer(message.data as SessionDescriptionData | undefined)
      break
    case 'ANSWER':
      void handleRemoteAnswer(message.data as SessionDescriptionData | undefined)
      break
    case 'ICE':
      void handleRemoteIce(message.data as IceCandidateData | undefined)
      break
    case 'AI_SAY':
      handleAiSay((message.data ?? {}) as AiSayData)
      break
    case 'AI_FINISHED':
      handleAiFinished(message.data as AiFinishedData | undefined)
      break
    case 'CHAT':
      handleChat((message.data ?? {}) as ChatData)
      break
    case 'ERROR':
      handleServerError(message.data as CallErrorData | undefined)
      break
    case 'HANGUP':
      handlePeerHangup()
      break
    default:
      break
  }
}

function startTicker(): void {
  stopTicker()
  callStartedAt = Date.now()
  elapsedSeconds.value = 0
  tickerId = window.setInterval(() => {
    elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - callStartedAt) / 1000))
    if (mode.value === 'AI' && questionStartMs > 0 && !aiFinished.value) {
      questionElapsed.value = Math.max(0, Math.floor((Date.now() - questionStartMs) / 1000))
    }
  }, 1000)
}

function stopTicker(): void {
  if (tickerId !== null) {
    window.clearInterval(tickerId)
    tickerId = null
  }
}

function canSpeak(): boolean {
  return ttsSupported && ttsEnabled.value
}

function speak(text: string): void {
  if (!canSpeak() || !text) return
  try {
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1
    utterance.pitch = 1
    utterance.onstart = () => {
      speaking.value = true
    }
    utterance.onend = () => {
      speaking.value = false
    }
    utterance.onerror = () => {
      speaking.value = false
    }
    window.speechSynthesis.speak(utterance)
  } catch {
    // 浏览器 TTS 不可用时静默降级，只显示字幕
    speaking.value = false
  }
}

function cancelSpeech(): void {
  speaking.value = false
  if (!ttsSupported) return
  try {
    window.speechSynthesis.cancel()
  } catch {
    // 忽略
  }
}

function toggleTts(): void {
  ttsEnabled.value = !ttsEnabled.value
  if (!ttsEnabled.value) {
    cancelSpeech()
    return
  }
  if (aiQuestion.value) speak(aiQuestion.value)
}

function replayQuestion(): void {
  if (!aiQuestion.value) {
    ElMessage.info('还没有题目可以重听')
    return
  }
  speak(aiQuestion.value)
}

function voiceFilename(blob: Blob): string {
  const type = (blob.type || '').toLowerCase()
  if (type.includes('mp4')) return 'answer.mp4'
  if (type.includes('ogg')) return 'answer.ogg'
  if (type.includes('wav')) return 'answer.wav'
  return 'answer.webm'
}

function isAsrUnavailable(error: ApiError): boolean {
  return (
    error.status === 501 ||
    error.status === 503 ||
    /未配置|未启用|not\s*configured|unsupported|ASR/i.test(error.message ?? '')
  )
}

function stopStreamTimer(): void {
  if (streamTimer !== null) {
    window.clearInterval(streamTimer)
    streamTimer = null
  }
}

async function startRecording(): Promise<void> {
  if (recording.value || transcribing.value || submitting.value) return
  if (!voiceSupported && !streamSupported) {
    ElMessage.warning(voiceError.value || '当前浏览器不支持录音，请使用文字作答')
    return
  }
  // 避免把 AI 的朗读声录进答案
  cancelSpeech()
  if (streamSupported) {
    streamReset()
    streamingMode.value = true
    streamElapsed.value = 0
    streamTimer = window.setInterval(() => {
      streamElapsed.value += 1
    }, 1000)
    await streamStart()
    if (streamError.value) {
      streamingMode.value = false
      stopStreamTimer()
      ElMessage.warning(streamError.value)
      return
    }
    return
  }
  await startVoiceRecorder()
  if (voiceError.value) ElMessage.warning(voiceError.value)
}

async function stopRecording(): Promise<void> {
  if (!recording.value) return
  if (streamingMode.value) {
    transcribing.value = true
    try {
      await streamStop()
      stopStreamTimer()
      streamingMode.value = false
      const text = streamText.value.trim()
      if (!text) {
        ElMessage.warning('没有识别到有效内容，可以直接输入文字作答')
        return
      }
      const existing = answerText.value.replace(/\s+$/, '')
      answerText.value = existing ? `${existing}\n${text}` : text
      asrUnavailable.value = false
      ElMessage.success('已转写，可修改后提交')
    } finally {
      transcribing.value = false
    }
    return
  }
  transcribing.value = true
  try {
    const blob = await stopVoiceRecorder()
    if (!blob.size) {
      ElMessage.warning('没有录到声音，请重试或直接输入文字')
      return
    }
    const transcription = await transcribeAudio(blob, voiceFilename(blob))
    const text = (transcription.text ?? '').trim()
    if (!text) {
      ElMessage.warning('没有识别到有效内容，可以直接输入文字作答')
      return
    }
    const existing = answerText.value.replace(/\s+$/, '')
    answerText.value = existing ? `${existing}\n${text}` : text
    if (transcription.audioPath) audioPath.value = transcription.audioPath
    asrUnavailable.value = false
    ElMessage.success('已转写，可修改后提交')
  } catch (error) {
    if (error instanceof ApiError && isAsrUnavailable(error)) {
      asrUnavailable.value = true
      ElMessage.warning('语音转写暂不可用，请直接用文字作答')
    } else {
      ElMessage.error(error instanceof ApiError ? error.message : '语音转写失败，请改用文字作答')
    }
  } finally {
    transcribing.value = false
  }
}

async function resolveQuestionId(): Promise<string> {
  if (aiQuestionId.value) return aiQuestionId.value
  const sessionId = practiceSessionId.value
  if (!sessionId) return ''
  try {
    const current = await fetchCurrentQuestion(sessionId)
    aiQuestionId.value = current.questionId
    if (!aiQuestion.value) aiQuestion.value = current.content
    if (current.seq > 0) aiSeq.value = current.seq
    if (current.totalQuestions > 0) aiTotal.value = current.totalQuestions
    if (questionStartMs === 0) {
      questionStartMs = Date.now()
      questionElapsed.value = 0
    }
    return current.questionId
  } catch {
    return ''
  }
}

async function syncCurrentQuestion(): Promise<void> {
  const sessionId = practiceSessionId.value
  if (!sessionId) return
  try {
    const current = await fetchCurrentQuestion(sessionId)
    aiQuestionId.value = current.questionId
    if (current.seq > 0) aiSeq.value = current.seq
    if (current.totalQuestions > 0) aiTotal.value = current.totalQuestions
    if (!aiQuestion.value) aiQuestion.value = current.content
  } catch {
    ElMessage.error('同步当前题目失败，请稍后重试')
  }
}

async function submitAnswer(): Promise<void> {
  if (!canSubmit.value) return
  const sessionId = practiceSessionId.value
  if (!sessionId) {
    ElMessage.error('缺少练习会话，暂时无法提交回答')
    return
  }
  const content = answerText.value.trim()
  if (!content) return

  submitting.value = true
  try {
    const questionId = await resolveQuestionId()
    if (!questionId) {
      ElMessage.warning('还没有拿到当前题目，请等 AI 出题后再提交')
      return
    }
    const body: PracticeAnswerRequest = {
      questionId,
      answer: content,
      thinkingSeconds: Math.max(0, Math.floor((Date.now() - questionStartMs) / 1000)),
    }
    if (audioPath.value) body.audioPath = audioPath.value

    const feedback = await submitPracticeAnswer(sessionId, body)
    answerResult.value = feedback
    submittedAnswer.value = content
    audioPath.value = ''
    if (feedback.finished) {
      aiFinished.value = true
      awaitingNext.value = false
      clearNextQuestionTimer()
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      ElMessage.warning(error.message || '题目已变化，正在同步最新题目')
      await syncCurrentQuestion()
    } else {
      ElMessage.error(error instanceof ApiError ? error.message : '提交失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

function requestNext(): void {
  if (awaitingNext.value || aiFinished.value) return
  if (!socketConnected.value) {
    ElMessage.warning('信令连接已断开，正在重连，请稍后再试')
    return
  }
  if (!sendCallMessage('AI_NEXT')) {
    ElMessage.error('发送下一题请求失败，请稍后重试')
    return
  }
  awaitingNext.value = true
  clearNextQuestionTimer()
  nextQuestionTimer = window.setTimeout(() => {
    nextQuestionTimer = null
    awaitingNext.value = false
    ElMessage.info('AI 还在准备下一题，请稍后重试')
  }, NEXT_QUESTION_TIMEOUT_MS)
}

function onAnswerKeydown(event: Event): void {
  const keyboard = event as KeyboardEvent
  if (keyboard.isComposing) return
  if ((keyboard.ctrlKey || keyboard.metaKey) && keyboard.key === 'Enter') {
    keyboard.preventDefault()
    void submitAnswer()
  }
}

async function copyText(text: string, successTip: string): Promise<void> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.setAttribute('readonly', 'readonly')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success(successTip)
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function copyRoomCode(): void {
  if (!roomCode.value) return
  void copyText(roomCode.value, '房间码已复制')
}

function copyInviteLink(): void {
  if (!inviteLink.value) return
  void copyText(inviteLink.value, '加入链接已复制，发给朋友即可')
}

function reconnectSignaling(): void {
  if (leaving) return
  ElMessage.info('正在重新连接信令服务…')
  connectSignaling()
}

function clearNextQuestionTimerAndSpeech(): void {
  clearNextQuestionTimer()
  cancelSpeech()
}

function isStale(token: number): boolean {
  return !alive || token !== bootToken
}

function cleanupRuntime(): void {
  // 让仍在进行中的初始化（等待接口/权限）失效，避免卸载后继续开连接
  bootToken += 1
  stopTicker()
  clearFallbackOffer()
  clearNextQuestionTimerAndSpeech()
  resetPeer()
  closeSocket()
  cancelVoiceRecorder()
  stopStreamTimer()
  void streamStop()
  stopMediaStream(localStream.value)
  localStream.value = null
  remoteStream.value = null
  detachRemoteVideo()
  const local = localVideoEl.value
  if (local) local.srcObject = null
  deferredOffers.length = 0
  deferredCandidates.length = 0
  needsAudioUnlock.value = false
  // 清掉房间内状态，重进 / 重试时从 ROOM_STATE 重新开始
  selfId.value = ''
  selfUserId.value = ''
  hostUserId.value = ''
  peers.value = []
  peerHangup.value = false
  connectionHint.value = ''
  chatLines.value = []
  socketNotice.value = ''
}

/** 清空 AI 作答状态（重新进入房间 / 切题时调用） */
function resetAiState(): void {
  aiQuestion.value = ''
  aiQuestionId.value = ''
  aiSeq.value = 0
  aiTotal.value = 0
  aiFinished.value = false
  questionElapsed.value = 0
  questionStartMs = 0
  answerText.value = ''
  submittedAnswer.value = ''
  audioPath.value = ''
  answerResult.value = null
  awaitingNext.value = false
  asrUnavailable.value = false
  clearNextQuestionTimerAndSpeech()
}

async function releaseCall(notifyPeer: boolean): Promise<void> {
  if (leaving) return
  leaving = true
  const code = roomCode.value
  if (notifyPeer) sendCallMessage('HANGUP')
  cleanupRuntime()
  if (!code) return
  // 不阻塞离场：请求失败（房间已被对方结束）也不影响前端返回
  void endVideoRoom(code).catch(() => {})
}

async function confirmLeave(): Promise<void> {
  if (leaving) return
  try {
    await ElMessageBox.confirm(
      isAi.value
        ? '结束本次 AI 模拟面试并离开？未提交的回答不会保存。'
        : '挂断后房间会结束，确定离开吗？',
      '离开确认',
      { confirmButtonText: '离开', cancelButtonText: '继续通话', type: 'warning' },
    )
  } catch {
    return
  }
  await releaseCall(true)
  await router.replace({ name: 'video-lobby' })
}

async function goReview(): Promise<void> {
  const sessionId = practiceSessionId.value
  if (!sessionId) {
    ElMessage.warning('缺少练习会话，暂时无法查看复盘')
    return
  }
  await releaseCall(true)
  await router.push({ name: 'practice-review', params: { id: sessionId } })
}

function goLobby(): void {
  if (!leaving) {
    leaving = true
    cleanupRuntime()
    if (roomCode.value) void endVideoRoom(roomCode.value).catch(() => {})
  }
  void router.replace({ name: 'video-lobby' })
}

async function boot(): Promise<void> {
  if (initializing) return
  initializing = true
  cleanupRuntime()
  const token = bootToken
  leaving = false
  everConnected = false
  receivedOffer = false
  peerHangup.value = false
  resetAiState()
  bootStage.value = 'loading'
  bootError.value = ''
  mediaErrorText.value = ''

  if (!roomCode.value) {
    bootError.value = '缺少房间码，请从视频面试页重新创建房间'
    bootStage.value = 'error'
    initializing = false
    return
  }

  try {
    const detail = await fetchVideoRoom(roomCode.value)
    if (isStale(token)) return
    mode.value = detail?.mode === 'AI' ? 'AI' : 'HUMAN'
    practiceSessionId.value = detail?.practiceSessionId ? String(detail.practiceSessionId) : ''
    if (isEndedStatus(detail?.status)) {
      bootError.value = '该房间已结束，请返回视频面试重新创建房间'
      bootStage.value = 'error'
      return
    }

    if (mode.value === 'HUMAN') {
      if (!isWebRTCSupported()) {
        bootStage.value = 'unsupported'
        return
      }
      await loadIceServers()
      if (isStale(token)) return
    }

    const granted = await openLocalMedia()
    if (isStale(token)) {
      // 等待授权期间页面已离开：回收刚拿到的媒体轨道
      stopMediaStream(localStream.value)
      localStream.value = null
      return
    }
    if (!granted) {
      bootStage.value = 'media-error'
      return
    }

    bootStage.value = 'ready'
    await nextTick()
    if (isStale(token)) return
    bindLocalVideo()
    startTicker()
    connectSignaling()
  } catch (error) {
    if (isStale(token)) return
    if (error instanceof ApiError && error.status === 404) {
      bootError.value = '房间不存在或已失效，请返回视频面试重新创建'
    } else {
      bootError.value = error instanceof ApiError ? error.message : '进入视频通话失败，请稍后重试'
    }
    bootStage.value = 'error'
  } finally {
    initializing = false
  }
}

async function continueWithoutMedia(): Promise<void> {
  // AI 模式没有摄像头也能文字作答：跳过媒体采集继续
  if (!isAi.value) return
  if (initializing) return
  initializing = true
  cleanupRuntime()
  const token = bootToken
  leaving = false
  everConnected = false
  resetAiState()
  bootStage.value = 'loading'
  try {
    bootStage.value = 'ready'
    await nextTick()
    if (isStale(token)) return
    startTicker()
    connectSignaling()
  } finally {
    initializing = false
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (leaving || bootStage.value !== 'ready') return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (leaving) return true
  if (bootStage.value !== 'ready') {
    cleanupRuntime()
    return true
  }
  try {
    await ElMessageBox.confirm('离开将结束本次视频通话，确定离开吗？', '离开确认', {
      confirmButtonText: '离开',
      cancelButtonText: '继续通话',
      type: 'warning',
    })
  } catch {
    return false
  }
  await releaseCall(true)
  return true
})

onMounted(() => {
  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('beforeunload', handleBeforeUnload)
  void boot()
})

onUnmounted(() => {
  alive = false
  window.removeEventListener('beforeunload', handleBeforeUnload)
  document.body.style.overflow = previousBodyOverflow
  cleanupRuntime()
})
</script>

<style scoped lang="scss">
.call {
  position: fixed;
  inset: 0;
  z-index: 2000;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 50% 0%, #1d2433 0%, #0a0c11 62%),
    #0a0c11;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}

/* ---------- 兜底页面 ---------- */
.call-state {
  display: grid;
  place-items: center;
  min-height: 100%;
  padding: 24px;

  &__card {
    width: min(520px, 100%);
    padding: 32px 28px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 18px;
    background: rgba(23, 28, 39, 0.86);
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
    text-align: center;
  }

  &__icon {
    display: inline-grid;
    place-items: center;
    width: 68px;
    height: 68px;
    margin-bottom: 16px;
    border-radius: 50%;
    color: #fff;
    background: rgba(47, 107, 255, 0.22);

    &--warn {
      color: #ffd08a;
      background: rgba(217, 130, 43, 0.22);
    }
  }

  h1 {
    font-size: 20px;
    font-weight: 600;
  }

  &__desc {
    margin-top: 10px;
    font-size: 13px;
    line-height: 1.8;
    color: rgba(255, 255, 255, 0.68);
  }

  &__tips {
    margin: 14px 0 0;
    padding-left: 18px;
    text-align: left;
    font-size: 12px;
    line-height: 1.9;
    color: rgba(255, 255, 255, 0.58);
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
    margin-top: 22px;

    :deep(.el-button) {
      margin-left: 0;
    }
  }

  &__spinner {
    margin-bottom: 14px;
    color: #7aa6ff;
    animation: call-spin 1s linear infinite;
  }

  &--loading h1 {
    font-size: 18px;
  }
}

@keyframes call-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------- 画面区 ---------- */
.call__stage {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.call__remote {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
}

.call__waiting {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  padding: 96px 24px 150px;
  text-align: center;
  background:
    radial-gradient(circle at 50% 30%, rgba(74, 107, 180, 0.28), transparent 55%),
    linear-gradient(160deg, #161c29, #0b0e14);

  h2 {
    margin-top: 6px;
    font-size: 20px;
    font-weight: 600;
  }

  &-hint {
    max-width: 420px;
    font-size: 13px;
    line-height: 1.8;
    color: rgba(255, 255, 255, 0.6);
  }

  &-avatar {
    display: grid;
    place-items: center;
    width: 92px;
    height: 92px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: rgba(255, 255, 255, 0.72);
  }

  &-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
    margin-top: 8px;

    :deep(.el-button) {
      margin-left: 0;
    }
  }
}

.call__invite {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 16px 22px;
  border-radius: 14px;
  border: 1px dashed rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.04);

  &-label {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);
  }

  &-code {
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 0.16em;
    font-variant-numeric: tabular-nums;
  }

  &-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;

    :deep(.el-button) {
      margin-left: 0;
    }
  }
}

.call__ice-tip {
  margin-top: 6px;
  max-width: 440px;
  font-size: 11px;
  line-height: 1.7;
  color: rgba(255, 200, 120, 0.8);
}

.call__audio-unlock {
  position: absolute;
  z-index: 4;
  left: 50%;
  bottom: 150px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transform: translateX(-50%);
  padding: 9px 16px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

/* ---------- AI 舞台 ---------- */
.call__stage--ai {
  align-items: center;
}

.ai-stage {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 12px;
  width: min(680px, 100%);
  padding: 96px 24px 150px;
  text-align: center;

  &__avatar {
    position: relative;
    display: grid;
    place-items: center;
    width: clamp(92px, 12vw, 124px);
    height: clamp(92px, 12vw, 124px);
    border-radius: 50%;
    color: #fff;
    background: linear-gradient(160deg, #3b6ef5, #6f8cff);
    box-shadow: 0 18px 40px rgba(47, 107, 255, 0.35);
  }

  &__halo {
    position: absolute;
    inset: -10px;
    border-radius: 50%;
    border: 1px solid rgba(122, 166, 255, 0.5);
    opacity: 0;
  }

  &__avatar.is-live &__halo {
    animation: ai-halo 3.2s ease-out infinite;
  }

  &__avatar.is-speaking &__halo {
    animation: ai-halo 1.4s ease-out infinite;
  }

  &__name {
    font-size: 19px;
    font-weight: 600;
    letter-spacing: 0.04em;
  }

  &__state {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.55);
  }

  &__card {
    width: 100%;
    margin-top: 8px;
    padding: 18px 22px;
    border-radius: 16px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    background: rgba(255, 255, 255, 0.06);
    text-align: left;
    backdrop-filter: blur(10px);
  }

  &__card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  &__seq {
    padding: 2px 10px;
    border-radius: 999px;
    background: rgba(122, 166, 255, 0.18);
    color: #b9cdff;
    font-size: 12px;
    font-weight: 600;
  }

  &__qtime {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);
    font-variant-numeric: tabular-nums;
  }

  &__question {
    font-size: clamp(17px, 1.7vw, 23px);
    font-weight: 500;
    line-height: 1.75;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__wait {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.55);
  }

  &__finished {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-top: 8px;
    padding: 8px 14px;
    border-radius: 999px;
    background: rgba(31, 157, 85, 0.18);
    color: #7ce0a5;
    font-size: 13px;
  }
}

@keyframes ai-halo {
  0% {
    transform: scale(0.96);
    opacity: 0.7;
  }

  70% {
    transform: scale(1.28);
    opacity: 0;
  }

  100% {
    transform: scale(1.28);
    opacity: 0;
  }
}

/* ---------- 顶部栏 ---------- */
.call__top {
  position: absolute;
  z-index: 7;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: calc(14px + env(safe-area-inset-top)) 20px 30px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.66), rgba(0, 0, 0, 0));
  pointer-events: none;

  &-main {
    min-width: 0;
    pointer-events: auto;
  }

  &-actions {
    display: flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
    pointer-events: auto;
  }
}

.call__code {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);

  span {
    margin-left: 4px;
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 0.12em;
    font-variant-numeric: tabular-nums;
  }
}

.call__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.call__badge {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  border: 1px solid rgba(255, 255, 255, 0.18);

  &--ai {
    color: #b9cdff;
    background: rgba(47, 107, 255, 0.24);
    border-color: rgba(122, 166, 255, 0.4);
  }

  &--human {
    color: #a8e8c4;
    background: rgba(31, 157, 85, 0.2);
    border-color: rgba(101, 230, 164, 0.36);
  }
}

.call__timer {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
  font-variant-numeric: tabular-nums;
}

.call__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 11px;
  color: rgba(255, 255, 255, 0.8);
}

.call__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #65e6a4;
}

.call__status.is-warn .call__dot {
  background: #f0b429;
  animation: call-blink 1.6s ease-in-out infinite;
}

.call__status.is-error .call__dot {
  background: #f56c6c;
}

@keyframes call-blink {
  50% {
    opacity: 0.35;
  }
}

.call__action {
  --el-button-text-color: rgba(255, 255, 255, 0.82);
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.12);
  --el-button-active-text-color: #fff;
  --el-button-bg-color: transparent;
  --el-button-border-color: transparent;
  padding: 6px 8px;
  font-size: 12px;

  &--close {
    padding: 4px;
    font-size: 18px;
  }
}

.call__banner {
  position: absolute;
  z-index: 8;
  top: calc(92px + env(safe-area-inset-top));
  left: 50%;
  width: min(560px, calc(100% - 32px));
  transform: translateX(-50%);

  :deep(.el-alert) {
    border-radius: 10px;
  }

  :deep(.el-alert__content) {
    color: #fff;
  }

  :deep(.el-button.is-link) {
    color: #b9cdff;
    padding: 0;
    height: auto;
  }
}

/* ---------- 真人模式聊天 ---------- */
.call__chat {
  position: absolute;
  z-index: 6;
  left: 20px;
  bottom: calc(120px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: min(340px, 46vw);
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.42);
  font-size: 12px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(6px);

  strong {
    color: #b9cdff;
    font-weight: 500;
  }
}

/* ---------- AI 作答面板 ---------- */
.call__dock {
  position: absolute;
  z-index: 6;
  top: 104px;
  right: 20px;
  bottom: 112px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(380px, 34vw);
  padding: 18px;
  overflow-y: auto;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  background: rgba(19, 23, 32, 0.88);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(16px);

  :deep(.el-button.is-text) {
    --el-button-text-color: rgba(255, 255, 255, 0.7);
    --el-button-hover-text-color: #fff;
    --el-button-hover-bg-color: rgba(255, 255, 255, 0.08);
  }

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.16);
    border-radius: 3px;
  }
}

.dock {
  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  &__eyebrow {
    font-size: 14px;
    font-weight: 600;
  }

  &__sub {
    margin-top: 3px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);
  }

  &__finished {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 18px 6px;
    text-align: center;

    h3 {
      font-size: 17px;
      font-weight: 600;
    }

    p {
      font-size: 12px;
      line-height: 1.8;
      color: rgba(255, 255, 255, 0.62);
    }

    :deep(.el-button) {
      margin-left: 0;
    }
  }

  &__finished-icon {
    display: grid;
    place-items: center;
    width: 54px;
    height: 54px;
    border-radius: 50%;
    color: #ffd08a;
    background: rgba(217, 130, 43, 0.2);
  }

  &__score {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 12px 14px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.06);
  }

  &__score-value {
    font-size: 32px;
    font-weight: 700;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;

    small {
      margin-left: 2px;
      font-size: 12px;
      font-weight: 400;
      color: rgba(255, 255, 255, 0.55);
    }
  }

  &__comment {
    font-size: 13px;
    line-height: 1.75;
    color: rgba(255, 255, 255, 0.8);
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__answer {
    max-height: 110px;
    padding: 10px 12px;
    overflow-y: auto;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.05);
    font-size: 12px;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.68);
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__next-hint {
    padding: 10px 12px;
    border-radius: 10px;
    background: rgba(47, 107, 255, 0.14);
    color: #b9cdff;
    font-size: 12px;
    line-height: 1.6;
  }

  &__actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: auto;

    :deep(.el-button) {
      margin-left: 0;
    }
  }

  &__record {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.05);

    :deep(.el-button) {
      margin-left: 0;
      flex-shrink: 0;
    }
  }

  &__record-hint {
    min-width: 0;
    flex: 1;
    font-size: 11px;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.5);
  }

  &__asr {
    :deep(.el-alert__title) {
      font-size: 12px;
    }
  }

  // 流式转写实时字幕
  &__live {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px 12px;
    border-radius: 10px;
    background: rgba(64, 158, 255, 0.12);
    box-shadow: inset 0 0 0 1px rgba(64, 158, 255, 0.28);
  }

  &__live-dot {
    width: 7px;
    height: 7px;
    margin-top: 7px;
    flex-shrink: 0;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.35);
    transition: background 0.2s ease;

    &.is-active {
      background: #f56c6c;
      box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.22);
    }
  }

  &__live-text {
    flex: 1;
    min-width: 0;
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: #e8f0ff;
    word-break: break-word;
  }

  &__live-meta {
    flex-shrink: 0;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.45);
  }

  &__input {
    :deep(.el-textarea__inner) {
      padding: 12px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.06);
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
      color: #f2f4f8;
      font-size: 14px;
      line-height: 1.75;
    }

    :deep(.el-textarea__inner::placeholder) {
      color: rgba(255, 255, 255, 0.36);
    }

    :deep(.el-input__count) {
      background: transparent;
      color: rgba(255, 255, 255, 0.4);
    }
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;

    :deep(.el-button) {
      margin-left: 0;
      flex-shrink: 0;
    }
  }

  &__foot-hint {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.45);
    font-variant-numeric: tabular-nums;
  }

  &__waiting {
    margin-top: auto;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);
  }
}

/* ---------- 本地 PiP ---------- */
.call__pip {
  position: absolute;
  z-index: 6;
  right: 24px;
  bottom: calc(118px + env(safe-area-inset-bottom));
  width: clamp(104px, 12vw, 176px);
  aspect-ratio: 3 / 4;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: #11151d;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.5);

  &-video {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transform: scaleX(-1); /* 前置摄像头镜像 */
    display: block;
  }

  &-placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    gap: 4px;
    background: #141a24;
    color: rgba(255, 255, 255, 0.55);
    font-size: 11px;
    text-align: center;
  }

  &-name {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 14px 8px 5px;
    background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.62));
    font-size: 11px;
    text-align: center;
    color: rgba(255, 255, 255, 0.9);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* AI 模式：作答面板在右侧，PiP 放左下角，避免遮挡 */
.call--ai .call__pip {
  right: auto;
  left: 24px;
  bottom: 24px;
}

.call--ai .call__stage {
  padding-right: 400px;
}

/* ---------- 底部控制条 ---------- */
.call__controls {
  position: absolute;
  z-index: 8;
  left: 50%;
  bottom: calc(22px + env(safe-area-inset-bottom));
  display: flex;
  align-items: flex-start;
  gap: clamp(12px, 2vw, 24px);
  transform: translateX(-50%);
  padding: 10px 18px 8px;
  border-radius: 999px;
  background: rgba(16, 20, 28, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(14px);
}

.ctrl {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  width: 68px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #fff;
  font: inherit;
  cursor: pointer;

  &__btn {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.15);
    color: #fff;
    transition:
      background 0.16s ease,
      color 0.16s ease,
      transform 0.16s ease;
  }

  &:hover &__btn {
    background: rgba(255, 255, 255, 0.24);
  }

  &:active &__btn {
    transform: scale(0.94);
  }

  &__label {
    font-size: 11px;
    line-height: 1.2;
    color: rgba(255, 255, 255, 0.72);
    white-space: nowrap;
  }

  &.is-off &__btn {
    background: #fff;
    color: #1f2329;
  }

  &.is-off &__label {
    color: rgba(255, 255, 255, 0.86);
  }

  &--hangup &__btn {
    width: 56px;
    height: 56px;
    background: #f04b4b;
  }

  &--hangup:hover &__btn {
    background: #ff5c5c;
  }
}

/* ---------- 响应式：窄屏面板改为底部浮层，控制条不重叠 ---------- */
@media (max-width: 1024px) {
  .call__top {
    padding: calc(12px + env(safe-area-inset-top)) 14px 24px;
  }

  .call__action {
    padding: 6px;
  }

  .call__banner {
    top: calc(86px + env(safe-area-inset-top));
  }

  .call--ai .call__stage {
    align-items: flex-start;
    padding-right: 0;
  }

  .call__dock {
    top: auto;
    left: 12px;
    right: 12px;
    bottom: calc(112px + env(safe-area-inset-bottom));
    width: auto;
    max-height: 40vh;
    padding: 14px;
    border-radius: 16px;
  }

  .call--ai .ai-stage {
    justify-content: flex-start;
    padding: 92px 16px 0;
  }

  .call--ai .call__pip {
    top: calc(88px + env(safe-area-inset-top));
    left: auto;
    right: 12px;
    bottom: auto;
    width: clamp(84px, 22vw, 120px);
  }

  .call__pip {
    right: 12px;
    bottom: calc(106px + env(safe-area-inset-bottom));
    width: clamp(88px, 24vw, 132px);
    border-radius: 12px;
  }

  .call__waiting {
    padding: 88px 18px 140px;
  }

  .call__chat {
    left: 12px;
    bottom: calc(110px + env(safe-area-inset-bottom));
    max-width: 44vw;
    max-height: 120px;
    overflow: hidden;
  }

  .call__audio-unlock {
    bottom: calc(112px + env(safe-area-inset-bottom));
  }
}

@media (max-width: 560px) {
  .call__top {
    padding: calc(10px + env(safe-area-inset-top)) 10px 20px;
  }

  .call__code {
    font-size: 11px;

    span {
      font-size: 13px;
    }
  }

  .call__meta {
    gap: 6px;
  }

  .call__badge {
    padding: 1px 7px;
    font-size: 10px;
  }

  .call__timer,
  .call__status {
    font-size: 10px;
  }

  .call__status {
    padding: 2px 7px;
  }

  .call__controls {
    gap: 8px;
    padding: 8px 12px 6px;
  }

  .ctrl {
    width: 58px;

    &__btn {
      width: 46px;
      height: 46px;
    }

    &--hangup &__btn {
      width: 50px;
      height: 50px;
    }
  }

  .ctrl__label {
    font-size: 10px;
  }

  .call__dock {
    left: 8px;
    right: 8px;
    bottom: calc(110px + env(safe-area-inset-bottom));
    max-height: 40vh;
  }

  .call--ai .ai-stage {
    padding-top: 84px;
  }

  .call--ai .call__pip {
    top: calc(80px + env(safe-area-inset-top));
    width: 76px;
  }

  .call__pip {
    width: 82px;
    bottom: calc(108px + env(safe-area-inset-bottom));
  }

  .ai-stage {
    gap: 8px;

    &__card {
      padding: 14px 16px;
    }

    &__question {
      font-size: 16px;
    }
  }
}

@media (max-height: 720px) {
  .call__dock {
    max-height: 36vh;
  }

  .call--ai .ai-stage {
    padding-top: 78px;
  }

  .ai-stage__avatar {
    width: 72px;
    height: 72px;
  }

  .ai-stage__name {
    font-size: 16px;
  }

  .ai-stage__card {
    padding: 14px 16px;
  }

  .ai-stage__question {
    font-size: 15px;
  }
}

@media (max-height: 640px) {
  .call__dock {
    max-height: 36vh;
  }

  .ai-stage {
    gap: 6px;
    padding-top: 70px;
  }

  .ai-stage__avatar {
    width: 64px;
    height: 64px;
  }

  .ai-stage__name {
    font-size: 15px;
  }

  .ai-stage__state {
    display: none;
  }

  .ai-stage__card {
    margin-top: 2px;
    padding: 12px 14px;
  }

  .ai-stage__question {
    font-size: 15px;
  }

  .call--ai .ai-stage {
    padding-top: 70px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-stage__halo,
  .ai-stage__avatar.is-live .ai-stage__halo,
  .ai-stage__avatar.is-speaking .ai-stage__halo {
    animation: none;
  }

  .call-state__spinner {
    animation-duration: 2.4s;
  }

  .call__status.is-warn .call__dot {
    animation: none;
  }
}
</style>
