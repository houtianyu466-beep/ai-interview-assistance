<template>
  <div class="practice-room">
    <div v-if="bootLoading" class="room-loading card" v-loading="true" element-loading-text="正在进入练习房间…" />

    <EmptyState
      v-else-if="loadError"
      title="进入练习失败"
      :description="loadError"
      :icon="Warning"
    >
      <template #action>
        <div class="flex-center gap-2 room-error-actions">
          <el-button type="primary" @click="bootstrap">重试</el-button>
          <el-button @click="router.push({ name: 'practice-home' })">返回练习列表</el-button>
        </div>
      </template>
    </EmptyState>

    <EmptyState
      v-else-if="!session || !question"
      title="题目还没有准备好"
      description="请稍后重试，或返回练习列表继续未完成的练习。"
      :icon="Warning"
    >
      <template #action>
        <div class="flex-center gap-2 room-error-actions">
          <el-button type="primary" @click="bootstrap">重试</el-button>
          <el-button @click="router.push({ name: 'practice-home' })">返回练习列表</el-button>
        </div>
      </template>
    </EmptyState>

    <template v-else>
      <header class="room-banner card">
        <div class="room-banner__main">
          <div class="room-banner__eyebrow">
            <span class="room-banner__live" aria-hidden="true" />
            练习房间
            <span class="room-banner__mode">{{ modeLabel(session.mode) }}</span>
          </div>
          <h1 class="room-banner__title">{{ session.targetTitle }}</h1>
          <p v-if="session.targetSubtitle" class="room-banner__subtitle">{{ session.targetSubtitle }}</p>
          <div class="room-banner__meta">
            <span class="room-banner__meta-item">
              <el-icon><Timer /></el-icon>
              已用时 {{ sessionClock }}
            </span>
            <span class="room-banner__meta-item">
              <el-icon><DocumentChecked /></el-icon>
              第 {{ currentSeq }} / {{ totalQuestions }} 题
            </span>
            <span v-if="session.answeredCount > 0" class="room-banner__meta-item room-banner__meta-item--muted">
              已作答 {{ session.answeredCount }} 题
            </span>
          </div>
          <el-progress
            class="room-banner__progress"
            :percentage="progressPercent"
            :stroke-width="5"
            :show-text="false"
            color="#7aa6ff"
          />
        </div>
        <div class="room-banner__actions">
          <el-button
            type="danger"
            plain
            :loading="finishing"
            :disabled="submitting || transcribing || voiceRecording"
            @click="finishPractice"
          >
            结束练习
          </el-button>
        </div>
      </header>

      <div class="room-grid">
        <main class="room-main">
          <section class="question-card card">
            <div class="question-card__head">
              <span class="question-card__seq">第 {{ question.seq }} 题</span>
              <el-tag v-if="question.category" size="small" type="info" effect="plain">
                {{ categoryLabel(question.category) }}
              </el-tag>
              <span
                v-if="difficulty > 0"
                class="question-card__difficulty"
                :aria-label="`难度 ${difficulty} / 5，${difficultyText}`"
              >
                <span class="question-card__stars" aria-hidden="true">{{ difficultyStars }}</span>
                <span class="question-card__difficulty-text">{{ difficultyText }}</span>
              </span>
            </div>
            <p class="question-card__content">{{ question.content }}</p>
            <p class="question-card__note">
              <el-icon><MagicStick /></el-icon>
              答题前不展示参考答案；先说出自己的理解，再看反馈
            </p>
          </section>

          <div class="answer-toolbar card">
            <div class="answer-toolbar__text">
              <strong>需要一点帮助？</strong>
              <span class="muted">提示不会自动填入答案，跳过不计分</span>
            </div>
            <div class="answer-toolbar__actions">
              <el-button :icon="ChatDotRound" @click="coachVisible = true">问教练</el-button>
              <el-button
                :icon="MagicStick"
                :loading="hintLoading"
                :disabled="!canUseToolbar"
                @click="loadHint"
              >
                {{ hint ? '再要一个提示' : '给我提示' }}
              </el-button>
              <el-button
                :icon="DArrowRight"
                :loading="skipping"
                :disabled="!canUseToolbar || skipping"
                @click="skipQuestion"
              >
                跳过本题
              </el-button>
              <el-button
                type="danger"
                plain
                :icon="CircleClose"
                :loading="finishing"
                :disabled="submitting || finishing || transcribing || voiceRecording"
                @click="finishPractice"
              >
                结束练习
              </el-button>
            </div>
          </div>

          <el-alert
            v-if="hint"
            class="hint-alert"
            type="info"
            show-icon
            title="提示"
            @close="dismissHint"
          >
            <p class="pre-wrap">{{ hint }}</p>
          </el-alert>

          <section v-if="!result" class="answer-card card">
            <div class="answer-card__head">
              <div>
                <h2 class="answer-card__title">我的回答</h2>
                <p class="muted answer-card__hint">建议先给结论，再分点展开；不会时可以先要提示</p>
              </div>
              <span class="answer-card__timer">
                <el-icon><Clock /></el-icon>
                {{ questionClock }}
              </span>
            </div>

            <el-input
              v-model="answer"
              class="answer-card__input"
              type="textarea"
              :rows="9"
              maxlength="10000"
              show-word-limit
              :disabled="submitting || transcribing || voiceRecording"
              placeholder="输入你的回答……（Ctrl / Cmd + Enter 提交）"
              @keydown="onAnswerKeydown"
            />

            <div class="voice-panel">
              <div class="voice-panel__actions">
                <template v-if="voiceRecording">
                  <el-button
                    type="danger"
                    :icon="VideoPause"
                    class="voice-panel__record"
                    aria-label="停止录音并转写"
                    @click="stopRecording"
                  >
                    停止录音并转写（{{ voiceClock }}）
                  </el-button>
                  <span class="voice-panel__status" role="status" aria-live="polite">
                    <span class="voice-panel__pulse" aria-hidden="true" />
                    正在录音，请开始作答
                  </span>
                </template>
                <template v-else>
                  <el-button
                    type="primary"
                    plain
                    :icon="Microphone"
                    :loading="transcribing"
                    :disabled="!voiceSupported || transcribing || submitting"
                    class="voice-panel__record"
                    :aria-label="transcribing ? '正在转写语音' : '开始语音作答录音'"
                    @click="startRecording"
                  >
                    {{ transcribing ? '正在转写…' : '语音作答' }}
                  </el-button>
                  <span v-if="transcribing" class="muted" role="status" aria-live="polite">
                    正在把录音转成文字，请稍候…
                  </span>
                  <span v-else class="muted">录音会转写为文字，可修改后再提交</span>
                </template>
              </div>
              <el-alert
                v-if="!voiceSupported || voiceError"
                class="voice-panel__alert"
                type="warning"
                :closable="false"
                show-icon
                :title="voiceError || '当前浏览器不支持录音，请改用文字作答'"
              />
            </div>

            <div class="answer-card__footer">
              <span class="muted">本题用时 {{ questionClock }}，提交后不可修改</span>
              <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitAnswer">
                提交回答
              </el-button>
            </div>
          </section>

          <section v-else-if="result.finished" class="finished-card card">
            <div class="finished-card__icon">
              <el-icon><Trophy /></el-icon>
            </div>
            <h2>练习结束</h2>
            <p class="muted">你已完成本次练习，去复盘看看整体表现、逐题参考答案和下一步建议。</p>
            <el-button type="primary" size="large" :icon="ArrowRight" @click="goReview">
              查看复盘
            </el-button>
          </section>

          <section v-else class="submitted-card card">
            <div class="submitted-card__head">
              <h2>我的回答</h2>
              <el-tag type="success" effect="light" round>已提交</el-tag>
            </div>
            <p class="pre-wrap submitted-card__text">{{ answer }}</p>
            <p class="muted submitted-card__hint">右侧是本题反馈，确认后进入下一题。</p>
          </section>
        </main>

        <aside class="room-side">
          <AnswerFeedback v-if="result" :result="result" />

          <section v-else class="tips-card card">
            <h2 class="tips-card__title">练习小贴士</h2>
            <ul class="tips-card__list">
              <li>先给结论，再用 2-3 个要点支撑</li>
              <li>不会时先要提示，不要空着提交</li>
              <li>录音转写后可以修改，确认后再提交</li>
              <li>本题提交后会立即得到四维反馈</li>
            </ul>
          </section>

          <section v-if="result && !result.finished" class="feedback-actions card">
            <p class="feedback-actions__title">继续练习</p>
            <p class="muted feedback-actions__desc">看完反馈后，进入下一题。</p>
            <el-button type="primary" class="w-full" :icon="ArrowRight" @click="goNext">
              下一题
            </el-button>
          </section>
        </aside>
      </div>

      <el-drawer
        v-model="coachVisible"
        class="coach-drawer"
        title="AI 面试教练"
        direction="rtl"
        :size="coachDrawerSize"
        :destroy-on-close="false"
        append-to-body
      >
        <CoachPanel :session-id="sessionId" :question="question?.content" />
      </el-drawer>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  ChatDotRound,
  CircleClose,
  Clock,
  DArrowRight,
  DocumentChecked,
  MagicStick,
  Microphone,
  Timer,
  Trophy,
  VideoPause,
  Warning,
} from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import AnswerFeedback from '@/components/practice/AnswerFeedback.vue'
import CoachPanel from '@/components/coach/CoachPanel.vue'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import { transcribeAudio } from '@/api/voice'
import {
  fetchCurrentQuestion,
  fetchPracticeHint,
  fetchPracticeSession,
  finishPracticeSession,
  skipPracticeQuestion,
  startPracticeSession,
  submitPracticeAnswer,
} from '@/api/practice'
import { ApiError } from '@/api/http'
import { DIFFICULTY_LABELS, INTERVIEW_MODE_META } from '@/utils/constants'
import type { AnswerResultVO, CurrentQuestionVO } from '@/types/api'
import type { PracticeAnswerRequest, PracticeSessionVO } from '@/types/practice'

interface PracticeSessionWithStartedAt extends PracticeSessionVO {
  /** 后端部分场景会返回，用于刷新后恢复整场计时 */
  startedAt?: string
}

const route = useRoute()
const router = useRouter()

const session = ref<PracticeSessionVO | null>(null)
const question = ref<CurrentQuestionVO | null>(null)
const bootLoading = ref(false)
const loadError = ref('')

const answer = ref('')
const submitting = ref(false)
const hint = ref('')
const hintLoading = ref(false)
const skipping = ref(false)
const finishing = ref(false)
const result = ref<AnswerResultVO | null>(null)
const audioPath = ref('')

const coachVisible = ref(false)
const isNarrowScreen = ref(false)
let narrowScreenQuery: MediaQueryList | null = null

const transcribing = ref(false)
const sessionElapsed = ref(0)
const questionElapsed = ref(0)
let sessionStartMs = Date.now()
let questionStartMs = Date.now()
let tickerId: number | null = null

const {
  supported: voiceSupported,
  recording: voiceRecording,
  elapsedSeconds: voiceElapsedSeconds,
  error: voiceError,
  start: startVoiceRecording,
  stop: stopVoiceRecording,
  cancel: cancelVoiceRecording,
} = useVoiceRecorder()

const sessionId = computed(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? raw[0] ?? '' : String(raw ?? '')
})

const totalQuestions = computed(
  () => question.value?.totalQuestions || session.value?.totalQuestions || 0,
)
const currentSeq = computed(() => question.value?.seq ?? Math.max(1, session.value?.currentIndex ?? 1))
const progressPercent = computed(() => {
  if (!totalQuestions.value) return 0
  return Math.min(100, Math.round((currentSeq.value / totalQuestions.value) * 100))
})

const sessionClock = computed(() => formatClock(sessionElapsed.value))
const questionClock = computed(() => formatClock(questionElapsed.value))
const voiceClock = computed(() => formatClock(voiceElapsedSeconds.value))

/** 窄屏（≤ 768px）下抽屉用 90% 宽度，避免 420px 挤压移动端 */
const coachDrawerSize = computed(() => (isNarrowScreen.value ? '90%' : '420px'))

const difficulty = computed(() => {
  const value = Number(question.value?.difficulty ?? 0)
  if (!Number.isFinite(value)) return 0
  return Math.min(5, Math.max(0, Math.round(value)))
})
const difficultyStars = computed(
  () => '★'.repeat(difficulty.value) + '☆'.repeat(5 - difficulty.value),
)
const difficultyText = computed(() => DIFFICULTY_LABELS[difficulty.value] || '难度未知')

const canUseToolbar = computed(
  () => !result.value && !submitting.value && !voiceRecording.value && !transcribing.value && !skipping.value,
)
const canSubmit = computed(
  () =>
    Boolean(answer.value.trim()) &&
    !submitting.value &&
    !transcribing.value &&
    !voiceRecording.value &&
    !result.value &&
    Boolean(question.value),
)

const CATEGORY_LABELS: Record<string, string> = {
  JAVA: 'Java',
  SPRING: 'Spring',
  MYSQL: 'MySQL',
  REDIS: 'Redis',
  DISTRIBUTED: '分布式',
  ALGORITHM: '算法',
  BEHAVIOR: '行为面试',
  FRONTEND: '前端',
  PRODUCT: '产品',
  DATA: '数据分析',
  TESTING: '测试',
  DEVOPS: '运维 / DevOps',
  GENERAL: '通用',
}

function modeLabel(mode?: string): string {
  if (!mode) return '练习'
  return INTERVIEW_MODE_META[mode as keyof typeof INTERVIEW_MODE_META] ?? mode
}

function categoryLabel(category: string): string {
  return CATEGORY_LABELS[category.toUpperCase()] ?? category
}

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds || 0))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function parseServerTime(value?: string | null): number | null {
  if (!value) return null
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const time = new Date(normalized).getTime()
  return Number.isNaN(time) ? null : time
}

function handleNarrowScreenChange(event: MediaQueryListEvent): void {
  isNarrowScreen.value = event.matches
}

function setupNarrowScreenWatcher(): void {
  if (typeof window.matchMedia !== 'function') return
  narrowScreenQuery = window.matchMedia('(max-width: 768px)')
  isNarrowScreen.value = narrowScreenQuery.matches
  narrowScreenQuery.addEventListener('change', handleNarrowScreenChange)
}

function teardownNarrowScreenWatcher(): void {
  narrowScreenQuery?.removeEventListener('change', handleNarrowScreenChange)
  narrowScreenQuery = null
}

function stopTicker(): void {
  if (tickerId !== null) {
    window.clearInterval(tickerId)
    tickerId = null
  }
}

function tick(): void {
  sessionElapsed.value = Math.max(0, Math.floor((Date.now() - sessionStartMs) / 1000))
  questionElapsed.value = Math.max(0, Math.floor((Date.now() - questionStartMs) / 1000))
}

function startTicker(): void {
  stopTicker()
  tick()
  tickerId = window.setInterval(tick, 1000)
}

function draftKey(questionId: string): string {
  return `practice:draft:${sessionId.value}:${questionId}`
}

function readDraft(questionId: string): string {
  try {
    return localStorage.getItem(draftKey(questionId)) ?? ''
  } catch {
    return ''
  }
}

function saveDraft(questionId: string, content: string): void {
  try {
    const key = draftKey(questionId)
    if (content) {
      localStorage.setItem(key, content)
    } else {
      localStorage.removeItem(key)
    }
  } catch {
    // 隐私模式或存储空间不足时静默失败，不影响作答
  }
}

function clearDraft(questionId: string): void {
  try {
    localStorage.removeItem(draftKey(questionId))
  } catch {
    // 同上，草稿清理失败不阻断提交
  }
}

function dismissHint(): void {
  hint.value = ''
}

function applyQuestion(next: CurrentQuestionVO): void {
  question.value = next
  result.value = null
  hint.value = ''
  audioPath.value = ''
  answer.value = readDraft(next.questionId)
  questionStartMs = Date.now()
  questionElapsed.value = 0
  if (session.value) {
    session.value.currentIndex = next.seq
    if (next.totalQuestions > 0) session.value.totalQuestions = next.totalQuestions
  }
}

async function bootstrap(): Promise<void> {
  const id = sessionId.value
  stopTicker()
  cancelVoiceRecording()
  question.value = null
  result.value = null
  hint.value = ''
  answer.value = ''
  audioPath.value = ''
  loadError.value = ''
  if (voiceSupported) voiceError.value = ''

  if (!id) {
    loadError.value = '缺少练习会话 ID，请从练习列表重新进入'
    return
  }

  bootLoading.value = true
  try {
    const detail = await fetchPracticeSession(id)
    session.value = detail

    if (detail.status === 'FINISHED') {
      await router.replace({ name: 'practice-review', params: { id } })
      return
    }
    if (detail.status === 'CANCELLED') {
      ElMessage.warning('本次练习已取消，请重新选择练习方向开始')
      await router.replace({ name: 'practice-home' })
      return
    }

    if (detail.status === 'CREATED') {
      const firstQuestion = await startPracticeSession(id)
      session.value = { ...detail, status: 'IN_PROGRESS' }
      sessionStartMs = Date.now()
      applyQuestion(firstQuestion)
    } else {
      const startedAt = parseServerTime((detail as PracticeSessionWithStartedAt).startedAt)
      sessionStartMs = startedAt ?? Date.now()
      const current = await fetchCurrentQuestion(id)
      applyQuestion(current)
    }
    startTicker()
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      ElMessage.error('练习不存在或已被删除')
      await router.replace({ name: 'practice-home' })
      return
    }
    loadError.value = error instanceof ApiError ? error.message : '进入练习失败，请稍后重试'
    ElMessage.error(loadError.value)
  } finally {
    bootLoading.value = false
  }
}

async function recoverCurrentQuestion(): Promise<void> {
  try {
    const current = await fetchCurrentQuestion(sessionId.value)
    applyQuestion(current)
    if (tickerId === null) startTicker()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '同步当前题目失败，请刷新页面重试')
  }
}

async function loadHint(): Promise<void> {
  if (!question.value || hintLoading.value || result.value) return
  hintLoading.value = true
  try {
    const data = await fetchPracticeHint(sessionId.value)
    hint.value = data.hint?.trim() || '暂时没有更多提示，先按自己的思路作答吧'
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '获取提示失败，请稍后重试')
  } finally {
    hintLoading.value = false
  }
}

async function submitAnswer(): Promise<void> {
  if (!canSubmit.value || !question.value) return
  const submittedQuestion = question.value
  const thinkingSeconds = Math.max(0, Math.floor((Date.now() - questionStartMs) / 1000))

  const body: PracticeAnswerRequest = {
    questionId: submittedQuestion.questionId,
    answer: answer.value.trim(),
    thinkingSeconds,
  }
  if (audioPath.value) body.audioPath = audioPath.value

  submitting.value = true
  try {
    const feedback = await submitPracticeAnswer(sessionId.value, body)
    result.value = feedback
    clearDraft(submittedQuestion.questionId)
    audioPath.value = ''

    if (session.value) {
      session.value.answeredCount = Math.min(
        session.value.totalQuestions,
        session.value.answeredCount + 1,
      )
    }

    if (feedback.finished) {
      if (session.value) session.value.status = 'FINISHED'
      stopTicker()
      cancelVoiceRecording()
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      ElMessage.warning(error.message || '本题已提交或题号已变化，正在同步最新题目')
      clearDraft(submittedQuestion.questionId)
      result.value = null
      await recoverCurrentQuestion()
    } else {
      ElMessage.error(error instanceof ApiError ? error.message : '提交失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

function goNext(): void {
  const current = question.value
  const next = result.value?.nextQuestion
  if (current) clearDraft(current.questionId)
  if (!next) {
    void recoverCurrentQuestion()
    return
  }
  applyQuestion(next)
  if (tickerId === null) startTicker()
}

async function skipQuestion(): Promise<void> {
  if (!question.value || result.value || skipping.value) return
  try {
    await ElMessageBox.confirm('跳过的题目不计分，且不能返回补答。确定跳过吗？', '跳过本题', {
      confirmButtonText: '跳过',
      cancelButtonText: '继续作答',
      type: 'warning',
    })
  } catch {
    return
  }

  const skippedQuestion = question.value
  skipping.value = true
  try {
    const outcome = await skipPracticeQuestion(sessionId.value)
    clearDraft(skippedQuestion.questionId)
    if (outcome.finished) {
      if (session.value) session.value.status = 'FINISHED'
      ElMessage.success('已完成全部题目，正在打开复盘')
      await goReview()
      return
    }
    if (outcome.nextQuestion) {
      applyQuestion(outcome.nextQuestion)
    } else {
      await recoverCurrentQuestion()
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '跳过本题失败，请稍后重试')
  } finally {
    skipping.value = false
  }
}

async function finishPractice(): Promise<void> {
  if (!session.value || finishing.value) return
  try {
    await ElMessageBox.confirm('结束后会生成本次练习复盘，之后不能再修改答案。确定结束吗？', '结束练习', {
      confirmButtonText: '结束并查看复盘',
      cancelButtonText: '继续练习',
      type: 'warning',
    })
  } catch {
    return
  }

  finishing.value = true
  try {
    await finishPracticeSession(sessionId.value)
    ElMessage.success('本次练习已结束')
    await goReview()
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      ElMessage.info('本次练习已结束，正在打开复盘')
      await goReview()
    } else {
      ElMessage.error(error instanceof ApiError ? error.message : '结束练习失败，请稍后重试')
    }
  } finally {
    finishing.value = false
  }
}

async function goReview(): Promise<void> {
  stopTicker()
  cancelVoiceRecording()
  await router.push({ name: 'practice-review', params: { id: sessionId.value } })
}

function startRecording(): void {
  if (!voiceSupported || result.value || submitting.value || transcribing.value) return
  void startVoiceRecording()
}

async function stopRecording(): Promise<void> {
  if (!voiceRecording.value) return
  // 先进入 loading，覆盖「停止录音 → 上传 → 转写」全过程，避免重复点击
  transcribing.value = true
  try {
    const blob = await stopVoiceRecording()
    if (!blob.size) {
      ElMessage.warning('没有录到声音，请重试或改用文字作答')
      return
    }
    const transcription = await transcribeAudio(blob, voiceFilename(blob))
    const text = (transcription.text ?? '').trim()
    if (!text) {
      ElMessage.warning('没有识别到有效内容，请改用文字作答')
      return
    }
    const existing = answer.value.replace(/\s+$/, '')
    answer.value = existing ? `${existing}\n${text}` : text
    if (transcription.audioPath) audioPath.value = transcription.audioPath
    ElMessage.success('已转写，可修改后提交')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '语音转写失败，请改用文字作答')
  } finally {
    transcribing.value = false
  }
}

function voiceFilename(blob: Blob): string {
  const type = (blob.type || '').toLowerCase()
  if (type.includes('mp4')) return 'answer.mp4'
  if (type.includes('ogg')) return 'answer.ogg'
  if (type.includes('wav')) return 'answer.wav'
  return 'answer.webm'
}

function onAnswerKeydown(event: Event): void {
  const keyboard = event as KeyboardEvent
  if (keyboard.isComposing) return
  if ((keyboard.ctrlKey || keyboard.metaKey) && keyboard.key === 'Enter') {
    keyboard.preventDefault()
    void submitAnswer()
  }
}

watch(answer, (value) => {
  const current = question.value
  if (!current || result.value) return
  saveDraft(current.questionId, value)
})

watch(
  () => route.params.id,
  (next, previous) => {
    if (next !== previous) void bootstrap()
  },
)

onMounted(() => {
  setupNarrowScreenWatcher()
  void bootstrap()
})

onUnmounted(() => {
  const current = question.value
  if (current && !result.value) saveDraft(current.questionId, answer.value)
  stopTicker()
  cancelVoiceRecording()
  teardownNarrowScreenWatcher()
})
</script>

<style scoped lang="scss">
.submitted-card {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    h2 {
      font-size: 15px;
      font-weight: 600;
    }
  }

  &__text {
    color: var(--ai-text);
    background: var(--ai-bg);
    border-radius: var(--ai-radius-md);
    padding: 14px 16px;
    min-height: 96px;
  }

  &__hint {
    margin-top: 12px;
    font-size: 12px;
  }
}

.practice-room {
  max-width: 1280px;
  margin: 0 auto;
}

.room-loading {
  min-height: 320px;
}

.room-error-actions {
  flex-wrap: wrap;
}

.room-banner {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  overflow: hidden;
  padding: 24px;
  color: #fff;
  background:
    radial-gradient(circle at 88% 10%, rgba(101, 145, 255, 0.45), transparent 42%),
    linear-gradient(135deg, #141b2d 0%, #1d2b4f 100%);

  &::after {
    position: absolute;
    right: -60px;
    bottom: -110px;
    width: 260px;
    height: 260px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 50%;
    content: '';
    pointer-events: none;
  }

  &__main {
    position: relative;
    z-index: 1;
    min-width: 0;
    flex: 1;
  }

  &__eyebrow {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    letter-spacing: 0.08em;
    color: rgba(255, 255, 255, 0.72);
  }

  &__live {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #65e6a4;
    box-shadow: 0 0 0 4px rgba(101, 230, 164, 0.14);
  }

  &__mode {
    padding: 1px 8px;
    border: 1px solid rgba(255, 255, 255, 0.24);
    border-radius: 999px;
    color: rgba(255, 255, 255, 0.9);
    letter-spacing: 0;
  }

  &__title {
    margin-top: 8px;
    font-size: 24px;
    font-weight: 600;
    line-height: 1.35;
  }

  &__subtitle {
    margin-top: 4px;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.68);
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 18px;
    margin-top: 14px;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.86);
  }

  &__meta-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-variant-numeric: tabular-nums;

    &--muted {
      color: rgba(255, 255, 255, 0.6);
    }
  }

  &__progress {
    max-width: 420px;
    margin-top: 14px;

    :deep(.el-progress-bar__outer) {
      background: rgba(255, 255, 255, 0.16);
    }
  }

  &__actions {
    position: relative;
    z-index: 1;
    flex-shrink: 0;
  }
}

.room-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
  margin-top: 16px;
}

.room-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.room-side {
  position: sticky;
  top: 0;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.question-card {
  padding: 26px 28px;
  border-left: 3px solid var(--ai-primary);

  &__head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
  }

  &__seq {
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 12px;
    font-weight: 600;
  }

  &__difficulty {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--ai-warning);
    font-size: 12px;
  }

  &__stars {
    letter-spacing: 1px;
    font-size: 13px;
  }

  &__difficulty-text {
    color: var(--ai-text-secondary);
  }

  &__content {
    margin-top: 18px;
    font-size: 21px;
    font-weight: 500;
    line-height: 1.75;
    word-break: break-word;
    white-space: pre-wrap;
  }

  &__note {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 18px;
    padding-top: 14px;
    border-top: 1px dashed var(--ai-border);
    font-size: 12px;
    color: var(--ai-text-secondary);
  }
}

.answer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px;

  &__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 13px;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.hint-alert {
  :deep(.el-alert__description) {
    margin-top: 4px;
    line-height: 1.7;
  }
}

.answer-card {
  padding: 22px;

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }

  &__title {
    font-size: 17px;
    font-weight: 600;
  }

  &__hint {
    margin-top: 4px;
    font-size: 12px;
  }

  &__timer {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    flex-shrink: 0;
    padding: 3px 10px;
    border-radius: 999px;
    background: var(--ai-bg);
    color: var(--ai-text-secondary);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  &__input {
    :deep(.el-textarea__inner) {
      padding: 14px;
      font-size: 15px;
      line-height: 1.8;
      border-radius: var(--ai-radius-md);
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 16px;
    font-size: 12px;
  }
}

.voice-panel {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: var(--ai-radius-md);
  background: var(--ai-bg);

  &__actions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    font-size: 12px;
  }

  &__record {
    flex-shrink: 0;
  }

  &__status {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: var(--ai-danger);
    font-size: 12px;
  }

  &__pulse {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--ai-danger);
    animation: voice-pulse 1.2s ease-out infinite;
  }

  &__alert {
    margin-top: 10px;
  }
}

@keyframes voice-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(214, 69, 69, 0.45);
  }

  70% {
    box-shadow: 0 0 0 7px rgba(214, 69, 69, 0);
  }

  100% {
    box-shadow: 0 0 0 0 rgba(214, 69, 69, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .voice-panel__pulse {
    animation: none;
    box-shadow: 0 0 0 4px rgba(214, 69, 69, 0.18);
  }
}

.tips-card {
  padding: 20px;

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__list {
    margin: 12px 0 0;
    padding-left: 18px;
    color: var(--ai-text-secondary);
    font-size: 13px;
    line-height: 2;
  }
}

.feedback-actions {
  padding: 18px;

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__desc {
    margin: 4px 0 12px;
    font-size: 12px;
  }
}

.finished-card {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 10px;
  padding: 40px 24px;
  text-align: center;

  &__icon {
    display: grid;
    place-items: center;
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: #e8f7ee;
    color: var(--ai-success);
    font-size: 30px;
  }

  h2 {
    font-size: 22px;
    font-weight: 600;
  }

  p {
    max-width: 460px;
    font-size: 13px;
    line-height: 1.8;
  }
}

@media (max-width: 1024px) {
  .room-grid {
    grid-template-columns: 1fr;
  }

  .room-side {
    position: static;
  }
}

@media (max-width: 768px) {
  .room-banner {
    flex-direction: column;
    padding: 20px;

    &__title {
      font-size: 21px;
    }

    &__actions {
      width: 100%;
    }
  }

  .question-card {
    padding: 20px;

    &__content {
      font-size: 18px;
    }
  }

  .answer-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .answer-card__footer {
    align-items: flex-start;
    flex-direction: column;

    .el-button {
      width: 100%;
    }
  }
}
</style>
