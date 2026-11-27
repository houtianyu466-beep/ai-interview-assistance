<template>
  <div class="video-lobby">
    <PageHeader title="视频面试" subtitle="像微信视频一样，和 AI 或朋友面对面练一场">
      <template #actions>
        <el-button :icon="Headset" @click="showHelp = !showHelp">
          {{ showHelp ? '收起使用说明' : '使用说明' }}
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="showHelp"
      class="video-lobby__help"
      type="info"
      :closable="false"
      show-icon
      title="开始前请确认"
    >
      <ul class="help-list">
        <li>首次进入需要允许浏览器使用<b>摄像头和麦克风</b>，请点「允许」；权限被拒绝后可在地址栏右侧重新开启。</li>
        <li>强烈建议<b>戴上耳机</b>：AI 语音朗读会被麦克风采进去，影响作答转写效果。</li>
        <li>真人模式需要对方打开<b>同一个加入链接</b>（或输入同一个房间码），双方都在线才能通话。</li>
        <li>当前浏览器需要支持 WebRTC；推荐最新版 Chrome / Edge / Safari。</li>
      </ul>
    </el-alert>

    <div class="video-lobby__grid">
      <!-- a) AI 模拟视频面试 -->
      <section class="card panel panel--ai">
        <header class="panel__head">
          <span class="panel__icon panel__icon--ai">
            <el-icon :size="22"><VideoCamera /></el-icon>
          </span>
          <div class="panel__titles">
            <h3>AI 模拟视频面试</h3>
            <p class="muted">AI 语音出题，你开口作答，提交后立即得分与点评</p>
          </div>
        </header>

        <el-skeleton v-if="targetsLoading" class="panel__skeleton" :rows="5" animated />

        <template v-else>
          <el-alert
            v-if="targetsError"
            type="error"
            :closable="false"
            show-icon
            :title="targetsError"
          >
            <template #default>
              <el-button link type="primary" size="small" @click="loadTargets">重新加载</el-button>
            </template>
          </el-alert>

          <EmptyState
            v-else-if="targets.length === 0"
            title="暂无可练习的方向"
            description="请稍后再试，或先去看看面试指南"
            :icon="Compass"
          >
            <template #action>
              <el-button type="primary" @click="loadTargets">重新加载</el-button>
            </template>
          </EmptyState>

          <el-form v-else label-position="top" class="panel__form" @submit.prevent>
            <el-form-item label="练习方向">
              <el-select
                v-model="aiForm.targetId"
                class="w-full"
                placeholder="选择一个练习方向"
                :disabled="aiSubmitting"
                @change="onTargetChange"
              >
                <el-option
                  v-for="target in targets"
                  :key="target.id"
                  :label="target.title"
                  :value="target.id"
                >
                  <span class="select-option">
                    <span>{{ target.title }}</span>
                    <span class="select-option__meta">{{ practiceCategoryLabel(target.category) }}</span>
                  </span>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="面试模式">
              <el-radio-group v-model="aiForm.mode" :disabled="aiSubmitting">
                <el-radio-button
                  v-for="mode in MODE_OPTIONS"
                  :key="mode.value"
                  :value="mode.value"
                >
                  {{ mode.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="题目数量">
              <div class="panel__count">
                <el-input-number
                  v-model="aiForm.totalQuestions"
                  :min="3"
                  :max="15"
                  :step="1"
                  :disabled="aiSubmitting"
                />
                <span class="muted">预计 {{ estimateMinutes }} 分钟</span>
              </div>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="aiError"
            class="panel__error"
            type="error"
            :closable="false"
            show-icon
            :title="aiError"
          />

          <div class="panel__foot">
            <span class="panel__hint">
              <el-icon><Microphone /></el-icon>
              {{ aiStep || '创建后进入全屏视频页，请允许摄像头与麦克风' }}
            </span>
            <el-button
              type="primary"
              size="large"
              :icon="VideoCamera"
              :loading="aiSubmitting"
              :disabled="targetsLoading || !aiForm.targetId"
              @click="startAiInterview"
            >
              {{ aiSubmitting ? '正在准备…' : '开始 AI 视频面试' }}
            </el-button>
          </div>
        </template>
      </section>

      <!-- b) 邀请朋友陪练 -->
      <section class="card panel panel--human">
        <header class="panel__head">
          <span class="panel__icon panel__icon--human">
            <el-icon :size="22"><UserFilled /></el-icon>
          </span>
          <div class="panel__titles">
            <h3>邀请朋友陪练</h3>
            <p class="muted">创建房间拿到房间码，对方打开链接即可 1:1 视频模拟</p>
          </div>
        </header>

        <div v-if="!createdRoom" class="invite-empty">
          <div class="invite-empty__art">
            <el-icon :size="30"><ChatDotRound /></el-icon>
          </div>
          <p class="invite-empty__title">还没有创建陪练房间</p>
          <p class="muted invite-empty__desc">
            创建后会生成 6-8 位房间码与加入链接，发给朋友即可开始。
          </p>
          <el-button
            type="primary"
            size="large"
            :icon="Plus"
            :loading="roomCreating"
            @click="createHumanRoom"
          >
            {{ roomCreating ? '正在创建房间…' : '创建陪练房间' }}
          </el-button>
          <el-alert
            v-if="roomError"
            class="panel__error"
            type="error"
            :closable="false"
            show-icon
            :title="roomError"
          />
        </div>

        <div v-else class="invite-room">
          <div class="invite-room__label">
            <span>房间码</span>
            <el-tag size="small" effect="plain" type="success" round>房间已创建</el-tag>
          </div>
          <div class="invite-room__code" aria-label="房间码">
            <span v-for="(char, index) in roomCodeChars" :key="`${char}-${index}`" class="invite-room__char">
              {{ char }}
            </span>
          </div>
          <p class="muted invite-room__tip">
            把房间码或下面的链接发给朋友，双方都进入后即可开始视频。
          </p>

          <div class="invite-room__actions">
            <el-button :icon="CopyDocument" @click="copyRoomCode">复制房间码</el-button>
            <el-button :icon="Link" @click="copyInviteLink">复制加入链接</el-button>
          </div>

          <div class="invite-room__link">
            <el-icon><Link /></el-icon>
            <span class="invite-room__link-text">{{ inviteLink }}</span>
          </div>

          <div class="invite-room__enter">
            <el-button type="primary" size="large" :icon="VideoCamera" @click="enterCreatedRoom">
              进入房间等待对方
            </el-button>
            <el-button text :loading="roomCreating" @click="createHumanRoom">重新创建</el-button>
          </div>
        </div>

        <el-divider class="invite-divider">或输入朋友的房间码加入</el-divider>

        <div class="join-box">
          <el-input
            v-model="joinCode"
            class="join-box__input"
            size="large"
            maxlength="12"
            clearable
            placeholder="输入 6-8 位房间码"
            :disabled="joining"
            @keyup.enter="joinRoom"
          />
          <el-button
            type="primary"
            size="large"
            :loading="joining"
            :disabled="!joinCode.trim()"
            @click="joinRoom"
          >
            加入房间
          </el-button>
        </div>
        <p v-if="joinError" class="join-box__error">{{ joinError }}</p>
      </section>
    </div>

    <section class="steps card">
      <h3 class="steps__title">视频面试怎么进行</h3>
      <ol class="steps__list">
        <li>
          <span class="steps__index">1</span>
          <div>
            <strong>允许摄像头与麦克风</strong>
            <p class="muted">浏览器弹窗选择「允许」，画面会先出现在右下角小窗。</p>
          </div>
        </li>
        <li>
          <span class="steps__index">2</span>
          <div>
            <strong>AI 模式：听题 → 作答</strong>
            <p class="muted">AI 语音读题并显示字幕；可录音转写后修改，提交立即得到得分与点评，点「下一题」继续。</p>
          </div>
        </li>
        <li>
          <span class="steps__index">3</span>
          <div>
            <strong>真人模式：等对方加入</strong>
            <p class="muted">房间创建者负责发起连接，双方都进入房间后会自动建立视频；挂断即结束房间。</p>
          </div>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Compass,
  CopyDocument,
  Headset,
  Link,
  Microphone,
  Plus,
  UserFilled,
  VideoCamera,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { createPracticeSession, fetchPracticeTargets } from '@/api/practice'
import { createVideoRoom, fetchVideoRoom } from '@/api/video'
import { ApiError } from '@/api/http'
import { practiceCategoryLabel } from '@/utils/constants'
import type { CreatePracticeRequest, PracticeTargetVO } from '@/types/practice'
import type { VideoRoomVO } from '@/types/video'

interface ModeOption {
  value: CreatePracticeRequest['mode']
  label: string
}

const MODE_OPTIONS: ModeOption[] = [
  { value: 'TECH', label: '技术面' },
  { value: 'HR', label: 'HR 面' },
  { value: 'COMPREHENSIVE', label: '综合面' },
]

const router = useRouter()

const showHelp = ref(true)

const targets = ref<PracticeTargetVO[]>([])
const targetsLoading = ref(true)
const targetsError = ref('')

const aiSubmitting = ref(false)
const aiError = ref('')
const aiStep = ref('')

const roomCreating = ref(false)
const roomError = ref('')
const createdRoom = ref<VideoRoomVO | null>(null)

const joinCode = ref('')
const joining = ref(false)
const joinError = ref('')

const aiForm = reactive({
  targetId: '',
  mode: 'COMPREHENSIVE' as CreatePracticeRequest['mode'],
  totalQuestions: 5,
})

const estimateMinutes = computed(() => {
  const count = Number.isFinite(aiForm.totalQuestions) ? aiForm.totalQuestions : 5
  return Math.max(5, Math.round(count * 2.5))
})

const roomCodeChars = computed(() => (createdRoom.value?.code ?? '').split(''))

const inviteLink = computed(() =>
  createdRoom.value ? `${window.location.origin}/call/${encodeURIComponent(createdRoom.value.code)}` : '',
)

function clampQuestions(value: number): number {
  const rounded = Number.isFinite(value) ? Math.round(value) : 5
  return Math.min(15, Math.max(3, rounded))
}

async function loadTargets(): Promise<void> {
  targetsLoading.value = true
  targetsError.value = ''
  try {
    const data = await fetchPracticeTargets()
    targets.value = Array.isArray(data) ? data : []
    if (!aiForm.targetId && targets.value.length > 0) {
      applyTarget(targets.value[0])
    }
  } catch (error) {
    targetsError.value = error instanceof ApiError ? error.message : '加载练习方向失败，请稍后重试'
  } finally {
    targetsLoading.value = false
  }
}

function applyTarget(target: PracticeTargetVO): void {
  aiForm.targetId = target.id
  aiForm.totalQuestions = clampQuestions(target.defaultQuestions || 5)
}

function onTargetChange(targetId: string): void {
  const target = targets.value.find((item) => item.id === targetId)
  if (target) aiForm.totalQuestions = clampQuestions(target.defaultQuestions || 5)
}

async function startAiInterview(): Promise<void> {
  if (aiSubmitting.value) return
  if (!aiForm.targetId) {
    ElMessage.warning('请先选择一个练习方向')
    return
  }

  aiSubmitting.value = true
  aiError.value = ''
  aiStep.value = '正在创建练习并生成题目（约 10-30 秒）…'
  try {
    const session = await createPracticeSession({
      targetId: aiForm.targetId,
      mode: aiForm.mode,
      totalQuestions: clampQuestions(aiForm.totalQuestions),
    })
    aiStep.value = '正在创建视频房间…'
    const room = await createVideoRoom({ mode: 'AI', practiceSessionId: session.id })
    if (!room?.code) {
      throw new Error('创建视频房间失败：未返回房间码')
    }
    ElMessage.success('房间已创建，正在进入视频面试')
    await router.push({ name: 'video-call', params: { code: room.code } })
  } catch (error) {
    aiError.value =
      error instanceof ApiError ? error.message : (error as Error)?.message || '创建视频面试失败，请稍后重试'
    ElMessage.error(aiError.value)
  } finally {
    aiSubmitting.value = false
    aiStep.value = ''
  }
}

async function createHumanRoom(): Promise<void> {
  if (roomCreating.value) return
  roomCreating.value = true
  roomError.value = ''
  try {
    const room = await createVideoRoom({ mode: 'HUMAN' })
    if (!room?.code) {
      throw new Error('创建房间失败：未返回房间码')
    }
    createdRoom.value = room
    ElMessage.success('房间已创建，把房间码或链接发给朋友吧')
  } catch (error) {
    roomError.value =
      error instanceof ApiError ? error.message : (error as Error)?.message || '创建房间失败，请稍后重试'
  } finally {
    roomCreating.value = false
  }
}

function enterCreatedRoom(): void {
  if (!createdRoom.value) return
  void router.push({ name: 'video-call', params: { code: createdRoom.value.code } })
}

function normalizeJoinCode(value: string): string {
  return value.replace(/\s+/g, '')
}

async function joinRoom(): Promise<void> {
  if (joining.value) return
  const code = normalizeJoinCode(joinCode.value)
  if (!code) {
    joinError.value = '请输入房间码'
    return
  }

  joining.value = true
  joinError.value = ''
  try {
    const room = await fetchVideoRoom(code)
    if (!room?.code) {
      throw new Error('房间不存在或已失效')
    }
    if (String(room.status).toUpperCase() === 'ENDED') {
      joinError.value = '该房间已结束，请让朋友重新创建'
      return
    }
    await router.push({ name: 'video-call', params: { code: room.code } })
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      joinError.value = '房间不存在，请检查房间码是否输入正确'
    } else {
      joinError.value = error instanceof ApiError ? error.message : (error as Error)?.message || '加入房间失败，请稍后重试'
    }
  } finally {
    joining.value = false
  }
}

async function copyText(text: string, successTip: string): Promise<void> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      // 旧浏览器 / 非 HTTPS 环境没有 Clipboard API，退回 execCommand
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
  if (!createdRoom.value) return
  void copyText(createdRoom.value.code, '房间码已复制')
}

function copyInviteLink(): void {
  if (!inviteLink.value) return
  void copyText(inviteLink.value, '加入链接已复制，发给朋友即可')
}

onMounted(() => {
  void loadTargets()
})
</script>

<style scoped lang="scss">
.video-lobby {
  max-width: 1240px;
  margin: 0 auto;
}

.video-lobby__help {
  margin-bottom: var(--ai-space-4);

  :deep(.el-alert__content) {
    width: 100%;
  }
}

.help-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 2;
}

.video-lobby__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: var(--ai-space-4);
  align-items: start;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--ai-space-4);
  padding: 24px 24px 22px;

  &--ai {
    border-top: 3px solid var(--ai-primary);
  }

  &--human {
    border-top: 3px solid #7c5cff;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: var(--ai-space-3);
  }

  &__icon {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border-radius: 12px;
    flex-shrink: 0;

    &--ai {
      color: var(--ai-primary);
      background: var(--ai-primary-soft);
    }

    &--human {
      color: #6b48ff;
      background: #f1edff;
    }
  }

  &__titles {
    min-width: 0;

    h3 {
      font-size: 17px;
      font-weight: 600;
    }

    p {
      margin-top: 4px;
      font-size: 13px;
      line-height: 1.6;
    }
  }

  &__skeleton {
    padding: 4px 0;
  }

  &__form {
    :deep(.el-form-item) {
      margin-bottom: 16px;
    }

    :deep(.el-form-item__label) {
      padding-bottom: 4px;
      font-size: 13px;
      color: var(--ai-text-secondary);
    }
  }

  &__count {
    display: flex;
    align-items: center;
    gap: var(--ai-space-3);
    font-size: 12px;
  }

  &__error {
    margin-top: 4px;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ai-space-3);
    margin-top: auto;
    padding-top: var(--ai-space-2);
    border-top: 1px dashed var(--ai-border);
  }

  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--ai-text-secondary);
  }
}

.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ai-space-4);

  &__meta {
    font-size: 12px;
    color: var(--ai-text-placeholder);
  }
}

.invite-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--ai-space-2);
  padding: var(--ai-space-4) var(--ai-space-2) var(--ai-space-6);
  text-align: center;

  &__art {
    display: grid;
    place-items: center;
    width: 68px;
    height: 68px;
    border-radius: 20px;
    margin-bottom: var(--ai-space-2);
    color: #6b48ff;
    background: #f1edff;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__desc {
    max-width: 320px;
    font-size: 13px;
    line-height: 1.7;
  }

  .el-button {
    margin-top: var(--ai-space-2);
  }
}

.invite-room {
  display: flex;
  flex-direction: column;
  gap: var(--ai-space-3);

  &__label {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    color: var(--ai-text-secondary);
  }

  &__code {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    padding: 16px 10px;
    border-radius: var(--ai-radius-md);
    background: linear-gradient(135deg, #f5f2ff, #eef3ff);
  }

  &__char {
    display: grid;
    place-items: center;
    min-width: 44px;
    height: 56px;
    padding: 0 6px;
    border-radius: 10px;
    background: #fff;
    box-shadow: var(--ai-shadow-card);
    color: #4b32b8;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 0.04em;
    font-variant-numeric: tabular-nums;
  }

  &__tip {
    font-size: 12px;
    text-align: center;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ai-space-2);

    .el-button {
      flex: 1;
      margin-left: 0;
    }
  }

  &__link {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border: 1px dashed var(--ai-border);
    border-radius: var(--ai-radius-md);
    color: var(--ai-text-secondary);
    font-size: 12px;
  }

  &__link-text {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__enter {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
    margin-top: 2px;

    .el-button:first-child {
      flex: 1;
    }
  }
}

.invite-divider {
  margin: var(--ai-space-2) 0 0;

  :deep(.el-divider__text) {
    font-size: 12px;
    color: var(--ai-text-placeholder);
  }
}

.join-box {
  display: flex;
  gap: var(--ai-space-2);

  &__input {
    flex: 1;
  }

  &__error {
    margin-top: 6px;
    font-size: 12px;
    color: var(--ai-danger);
  }
}

.steps {
  margin-top: var(--ai-space-4);

  &__title {
    font-size: 16px;
    font-weight: 600;
  }

  &__list {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--ai-space-4);
    margin: var(--ai-space-4) 0 0;
    padding: 0;
    list-style: none;

    li {
      display: flex;
      align-items: flex-start;
      gap: var(--ai-space-3);
    }

    strong {
      font-size: 14px;
      font-weight: 600;
    }

    p {
      margin-top: 4px;
      font-size: 12px;
      line-height: 1.7;
    }
  }

  &__index {
    display: grid;
    place-items: center;
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 13px;
    font-weight: 600;
  }
}

@media (max-width: 1024px) {
  .video-lobby__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .steps__list {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--ai-space-3);
  }
}

@media (max-width: 600px) {
  .panel {
    padding: 18px 16px;

    &__foot {
      align-items: stretch;
      flex-direction: column;
    }

    &__hint {
      order: 2;
    }
  }

  .join-box {
    flex-direction: column;

    .el-button {
      width: 100%;
      margin-left: 0;
    }
  }

  .invite-room__char {
    min-width: 34px;
    height: 46px;
    font-size: 21px;
  }
}
</style>
