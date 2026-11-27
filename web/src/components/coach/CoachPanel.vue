<template>
  <div class="coach-panel">
    <div v-if="question" class="coach-panel__context">
      <span class="coach-panel__context-label">当前题目</span>
      <p class="coach-panel__context-text" :title="question">{{ question }}</p>
    </div>

    <div ref="listRef" class="coach-panel__list" role="log" aria-live="polite">
      <div v-if="!messages.length" class="coach-empty">
        <div class="coach-empty__icon" aria-hidden="true">
          <el-icon><Service /></el-icon>
        </div>
        <p class="coach-empty__title">AI 面试教练</p>
        <p class="coach-empty__desc">
          针对当前题目追问思路、结构和表达；教练会结合知识库给建议，但不会直接替你写出完整答案。
        </p>
        <div class="coach-empty__suggestions">
          <button
            v-for="item in SUGGESTIONS"
            :key="item"
            type="button"
            class="coach-suggestion"
            :disabled="sending || !sessionId"
            @click="applySuggestion(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <article
        v-for="message in messages"
        :key="message.id"
        class="coach-msg"
        :class="message.role === 'user' ? 'is-user' : 'is-coach'"
      >
        <div
          class="coach-bubble"
          :class="[
            message.role === 'user' ? 'coach-bubble--user' : 'coach-bubble--coach',
            { 'is-error': message.error },
          ]"
        >
          <p class="coach-answer pre-wrap">{{ message.content }}</p>

          <div v-if="message.toolCalls.length" class="coach-tools">
            <el-tooltip
              v-for="(call, index) in message.toolCalls"
              :key="`${message.id}-tool-${index}`"
              :content="toolTooltip(call)"
              placement="top"
              :show-after="150"
            >
              <span class="coach-tool-badge">
                <el-icon><Cpu /></el-icon>
                {{ toolLabel(call) }}
              </span>
            </el-tooltip>
          </div>

          <div v-if="message.citations.length" class="coach-citations">
            <p class="coach-citations__label">引用 {{ message.citations.length }} 条</p>
            <div class="coach-citations__chips">
              <el-tooltip
                v-for="(citation, index) in message.citations"
                :key="`${message.id}-cite-${index}`"
                :content="citationTooltip(citation)"
                placement="top"
                :show-after="150"
              >
                <button
                  type="button"
                  class="coach-citation"
                  :class="{ 'is-open': isCitationOpen(message.id, index) }"
                  :aria-expanded="isCitationOpen(message.id, index)"
                  @click="toggleCitation(message.id, index)"
                >
                  <el-icon><Document /></el-icon>
                  <span class="coach-citation__title">{{ citation.title || '知识库片段' }}</span>
                </button>
              </el-tooltip>
            </div>
            <p
              v-for="(citation, index) in message.citations"
              v-show="isCitationOpen(message.id, index)"
              :key="`${message.id}-snippet-${index}`"
              class="coach-citation__snippet pre-wrap"
            >
              {{ citation.snippet }}
              <span class="coach-citation__score">相关度 {{ formatScore(citation.score) }}</span>
            </p>
          </div>

          <p v-if="message.fallback" class="coach-fallback">
            <el-icon><InfoFilled /></el-icon>
            <span>当前为知识库检索模式，配置模型后可获得更完整的教练回答</span>
          </p>

          <p v-if="!message.error && latencyText(message.latencyMs)" class="coach-meta">
            教练用时 {{ latencyText(message.latencyMs) }}
          </p>

          <div v-if="message.error" class="coach-error-actions">
            <el-button size="small" :icon="RefreshRight" :disabled="sending" @click="retry(message)">
              重试
            </el-button>
          </div>
        </div>
      </article>

      <article v-if="sending" class="coach-msg is-coach">
        <div class="coach-bubble coach-bubble--coach coach-thinking" role="status">
          <span class="coach-thinking__dots" aria-hidden="true"><i /><i /><i /></span>
          <span>教练正在思考…</span>
        </div>
      </article>
    </div>

    <p v-if="!sessionId" class="coach-panel__notice">缺少练习会话 ID，暂时无法向教练提问。</p>

    <div class="coach-panel__composer">
      <el-input
        v-model="draft"
        class="coach-panel__input"
        type="textarea"
        :rows="2"
        resize="none"
        maxlength="1000"
        :disabled="!sessionId"
        placeholder="针对这道题提问，例如：这道题怎么答更好？"
        @keydown="onComposerKeydown"
      />
      <div class="coach-panel__composer-footer">
        <span class="muted coach-panel__hint">Enter 发送 · Shift + Enter 换行</span>
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="sending"
          :disabled="!canSend"
          @click="sendDraft"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Cpu,
  Document,
  InfoFilled,
  Promotion,
  RefreshRight,
  Service,
} from '@element-plus/icons-vue'
import { askCoach } from '@/api/agent'
import { ApiError } from '@/api/http'
import { formatScore } from '@/utils/format'
import type { Citation, ToolCall } from '@/types/agent'

interface CoachMessage {
  id: string
  role: 'user' | 'coach'
  content: string
  citations: Citation[]
  toolCalls: ToolCall[]
  fallback?: boolean
  latencyMs?: number
  error?: boolean
  /** 失败请求的原始问题，用于原地重试 */
  retryText?: string
}

const props = defineProps<{
  sessionId?: string
  question?: string
}>()

const SUGGESTIONS = ['这道题怎么答更好？', '我的回答缺什么？', '帮我用 STAR 重讲一遍']

const TOOL_LABELS: Record<string, string> = {
  search_knowledge: '已检索知识库',
  search_knowledge_base: '已检索知识库',
  retrieve_knowledge: '已检索知识库',
}

const messages = ref<CoachMessage[]>([])
const draft = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)
const openCitationKey = ref('')

let messageSequence = 0
let listObserver: ResizeObserver | null = null
let lastListHeight = 0

const canSend = computed(
  () => Boolean(props.sessionId) && Boolean(draft.value.trim()) && !sending.value,
)

function nextId(): string {
  messageSequence += 1
  return `coach-${Date.now()}-${messageSequence}`
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const element = listRef.value
  if (!element) return
  element.scrollTop = element.scrollHeight
}

// 抽屉关闭时列表高度为 0；重新打开（高度恢复）时补一次滚动，
// 避免错过「请求在抽屉关闭期间返回」的回答
function observeListVisibility(): void {
  const element = listRef.value
  if (!element || typeof ResizeObserver === 'undefined') return
  listObserver = new ResizeObserver((entries) => {
    const height = entries[0]?.contentRect.height ?? 0
    const wasHidden = lastListHeight === 0
    lastListHeight = height
    if (wasHidden && height > 0) void scrollToBottom()
  })
  listObserver.observe(element)
}

onMounted(observeListVisibility)

onUnmounted(() => {
  listObserver?.disconnect()
  listObserver = null
})

function applySuggestion(text: string): void {
  if (sending.value || !props.sessionId) return
  draft.value = text
  void sendDraft()
}

function onComposerKeydown(event: Event): void {
  const keyboard = event as KeyboardEvent
  // 中文输入法组合期间的回车用于选词，不触发发送
  if (keyboard.isComposing) return
  if (keyboard.key !== 'Enter') return
  // Shift + Enter 保留换行
  if (keyboard.shiftKey) return
  // 发送中或内容为空时不拦截，保留默认换行行为
  if (!canSend.value) return
  keyboard.preventDefault()
  void sendDraft()
}

async function sendDraft(): Promise<void> {
  const text = draft.value.trim()
  if (!text || !canSend.value) return
  draft.value = ''
  await requestAnswer(text, true)
}

async function requestAnswer(text: string, appendUserMessage: boolean): Promise<void> {
  const sessionId = props.sessionId
  if (!sessionId || sending.value) return

  if (appendUserMessage) {
    messages.value.push({
      id: nextId(),
      role: 'user',
      content: text,
      citations: [],
      toolCalls: [],
    })
  }

  sending.value = true
  try {
    const response = await askCoach(sessionId, text)
    // 请求期间切换了练习会话时丢弃旧回答，避免串场
    if (props.sessionId !== sessionId) return
    const answer = (response?.answer ?? '').trim()
    messages.value.push({
      id: nextId(),
      role: 'coach',
      content: answer || '教练暂时没有返回内容，换个问法再试一次吧。',
      citations: Array.isArray(response?.citations) ? response.citations : [],
      toolCalls: Array.isArray(response?.toolCalls) ? response.toolCalls : [],
      fallback: Boolean(response?.fallback),
      latencyMs:
        typeof response?.latencyMs === 'number' && Number.isFinite(response.latencyMs)
          ? response.latencyMs
          : undefined,
    })
  } catch (error) {
    if (props.sessionId !== sessionId) return
    const message = error instanceof ApiError ? error.message : '向教练提问失败，请稍后重试'
    // 用户消息保留，追加一条错误消息并提供原地重试
    messages.value.push({
      id: nextId(),
      role: 'coach',
      content: message,
      citations: [],
      toolCalls: [],
      error: true,
      retryText: text,
    })
    ElMessage.error(message)
  } finally {
    sending.value = false
  }
}

function retry(message: CoachMessage): void {
  if (sending.value || !message.retryText) return
  const index = messages.value.findIndex((item) => item.id === message.id)
  if (index >= 0) messages.value.splice(index, 1)
  // 用户问题已经在列表里，重试时不再追加重复气泡
  void requestAnswer(message.retryText, false)
}

function citationKey(messageId: string, index: number): string {
  return `${messageId}:${index}`
}

function isCitationOpen(messageId: string, index: number): boolean {
  return openCitationKey.value === citationKey(messageId, index)
}

function toggleCitation(messageId: string, index: number): void {
  const key = citationKey(messageId, index)
  openCitationKey.value = openCitationKey.value === key ? '' : key
}

function extractToolCount(call: ToolCall): number | null {
  const source = `${call.resultSummary ?? ''} ${call.arguments ?? ''}`
  const matched = source.match(/(\d+)\s*(条|项|个)/)
  if (!matched) return null
  const count = Number(matched[1])
  return Number.isFinite(count) ? count : null
}

function toolLabel(call: ToolCall): string {
  const name = (call.name ?? '').trim()
  const normalized = name.toLowerCase()
  const base =
    TOOL_LABELS[normalized] ??
    (/(knowledge|search|retrieve)/.test(normalized)
      ? '已检索知识库'
      : name
        ? `已调用 ${name}`
        : '已调用工具')
  const count = extractToolCount(call)
  return count === null ? base : `${base} ${count} 条`
}

function clampText(value: string, max: number): string {
  const text = (value ?? '').trim()
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function toolTooltip(call: ToolCall): string {
  const parts = [call.name || '工具调用']
  if (call.arguments) parts.push(`参数：${clampText(call.arguments, 80)}`)
  if (call.resultSummary) parts.push(`结果：${clampText(call.resultSummary, 80)}`)
  return parts.join(' · ')
}

function citationTooltip(citation: Citation): string {
  return clampText(citation.snippet || citation.title || '', 140) || '知识库片段'
}

function latencyText(milliseconds?: number): string {
  if (typeof milliseconds !== 'number' || !Number.isFinite(milliseconds) || milliseconds <= 0) {
    return ''
  }
  if (milliseconds < 1000) return `${Math.round(milliseconds)} 毫秒`
  return `${(milliseconds / 1000).toFixed(1)} 秒`
}

// 消息或 loading 变化后自动滚到底部（无定时器，卸载即停止）
watch([() => messages.value.length, sending], () => {
  void scrollToBottom()
})

// 同一页面切换到另一场练习时清空对话，避免串场
watch(
  () => props.sessionId,
  (next, previous) => {
    if (next === previous) return
    messages.value = []
    draft.value = ''
    openCitationKey.value = ''
  },
)
</script>

<style scoped lang="scss">
.coach-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  &__context {
    flex-shrink: 0;
    margin-bottom: 12px;
    padding: 10px 12px;
    border-radius: var(--ai-radius-md);
    background: var(--ai-bg);
  }

  &__context-label {
    display: inline-block;
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--ai-primary);
  }

  &__context-text {
    display: -webkit-box;
    overflow: hidden;
    font-size: 13px;
    line-height: 1.6;
    color: var(--ai-text-secondary);
    word-break: break-word;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  &__list {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    gap: 14px;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px;
    overscroll-behavior: contain;
  }

  &__notice {
    flex-shrink: 0;
    margin-top: 10px;
    font-size: 12px;
    color: var(--ai-danger);
  }

  &__composer {
    flex-shrink: 0;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--ai-border);
  }

  &__input {
    :deep(.el-textarea__inner) {
      padding: 10px 12px;
      font-size: 14px;
      line-height: 1.7;
      border-radius: var(--ai-radius-md);
      resize: none;
    }
  }

  &__composer-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-top: 8px;
  }

  &__hint {
    font-size: 12px;
  }
}

.coach-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 8px;
  padding: 24px 8px 12px;
  text-align: center;

  &__icon {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 24px;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__desc {
    max-width: 300px;
    font-size: 12px;
    line-height: 1.7;
    color: var(--ai-text-secondary);
  }

  &__suggestions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    margin-top: 8px;
  }
}

.coach-suggestion {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--ai-border);
  border-radius: var(--ai-radius-md);
  background: var(--ai-bg-card);
  color: var(--ai-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    color 0.18s ease,
    background 0.18s ease;

  &:hover:not(:disabled) {
    border-color: var(--ai-primary);
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.coach-msg {
  display: flex;
  width: 100%;

  &.is-user {
    justify-content: flex-end;
  }

  &.is-coach {
    justify-content: flex-start;
  }
}

.coach-bubble {
  max-width: 92%;
  padding: 10px 12px;
  border-radius: var(--ai-radius-md);
  font-size: 13px;
  line-height: 1.75;

  &--user {
    border-top-right-radius: var(--ai-radius-sm);
    background: var(--ai-primary);
    color: #fff;
  }

  &--coach {
    border-top-left-radius: var(--ai-radius-sm);
    background: var(--ai-bg);
    color: var(--ai-text);

    &.is-error {
      border: 1px solid rgba(214, 69, 69, 0.28);
      background: #fdf3f3;
      color: var(--ai-danger);
    }
  }
}

.coach-answer {
  word-break: break-word;
}

.coach-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.coach-tool-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--ai-primary-soft);
  color: var(--ai-primary);
  font-size: 12px;
  line-height: 18px;
  cursor: default;
}

.coach-citations {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--ai-border);

  &__label {
    margin-bottom: 6px;
    font-size: 12px;
    color: var(--ai-text-secondary);
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}

.coach-citation {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 3px 9px;
  border: 1px solid var(--ai-border);
  border-radius: 999px;
  background: var(--ai-bg-card);
  color: var(--ai-text-secondary);
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    color 0.18s ease;

  &:hover,
  &.is-open {
    border-color: var(--ai-primary);
    color: var(--ai-primary);
  }

  &__title {
    overflow: hidden;
    max-width: 200px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__snippet {
    margin-top: 8px;
    padding: 8px 10px;
    border: 1px solid var(--ai-border);
    border-radius: var(--ai-radius-sm);
    background: var(--ai-bg-card);
    font-size: 12px;
    color: var(--ai-text-secondary);
  }

  &__score {
    display: block;
    margin-top: 4px;
    font-size: 11px;
    color: var(--ai-text-placeholder);
  }
}

.coach-fallback {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: var(--ai-radius-sm);
  background: #fff8ec;
  color: var(--ai-warning);
  font-size: 12px;
  line-height: 1.6;
}

.coach-meta {
  margin-top: 8px;
  font-size: 11px;
  color: var(--ai-text-placeholder);
}

.coach-error-actions {
  margin-top: 8px;
}

.coach-thinking {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ai-text-secondary);

  &__dots {
    display: inline-flex;
    gap: 3px;

    i {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: var(--ai-text-placeholder);
      animation: coach-dot 1.2s ease-in-out infinite;

      &:nth-child(2) {
        animation-delay: 0.15s;
      }

      &:nth-child(3) {
        animation-delay: 0.3s;
      }
    }
  }
}

@keyframes coach-dot {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .coach-thinking__dots i {
    animation: none;
  }
}

@media (max-width: 768px) {
  .coach-bubble {
    max-width: 96%;
  }

  .coach-empty {
    padding: 16px 4px 8px;
  }
}
</style>
