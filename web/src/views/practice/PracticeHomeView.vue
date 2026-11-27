<template>
  <div class="home">
    <PageHeader :title="greeting" subtitle="今天练一道，面试少慌一点">
      <template #actions>
        <el-button :icon="VideoCamera" @click="goVideoLobby">视频模拟面试</el-button>
        <el-button type="primary" :icon="VideoPlay" @click="goCreate">开始练习</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="sessionsError"
      type="error"
      :closable="false"
      show-icon
      class="mb-4"
      :title="sessionsError"
    >
      <template #default>
        <el-button link type="primary" size="small" @click="loadSessions">重新加载</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="sessionsLoading && !sessionsLoaded" class="card mb-4" :rows="3" animated />

    <section v-else-if="!sessionsError" class="continue card">
      <template v-if="activeSession">
        <div class="continue__info">
          <p class="continue__eyebrow">
            <el-icon><VideoPlay /></el-icon>
            继续练习
          </p>
          <h3 class="continue__title">{{ activeSession.targetTitle }}</h3>
          <p class="continue__desc">
            {{ activeSession.targetSubtitle || '接着上次的进度，把这场练习做完' }}
          </p>
          <div class="continue__progress">
            <el-progress
              :percentage="progressPercent"
              :stroke-width="8"
              :show-text="false"
              color="#ffffff"
            />
            <span class="continue__progress-text">
              已完成 {{ activeSession.answeredCount }} / {{ activeSession.totalQuestions }} 题
            </span>
          </div>
        </div>
        <el-button size="large" class="continue__button" @click="goRoom(activeSession.id)">
          继续练习
          <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </template>

      <template v-else>
        <div class="continue__info">
          <p class="continue__eyebrow">
            <el-icon><MagicStick /></el-icon>
            开始第一次练习
          </p>
          <h3 class="continue__title">还没有进行中的练习</h3>
          <p class="continue__desc">选一个方向，AI 会结合你的简历现场出题，练完给复盘。</p>
        </div>
        <el-button size="large" class="continue__button" @click="goCreate">
          选个方向开始
          <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </template>
    </section>

    <el-row v-if="!sessionsError" :gutter="16" class="stats">
      <el-col v-for="item in stats" :key="item.label" :xs="24" :sm="8">
        <div class="stat card">
          <div class="stat__icon" :style="{ background: item.bg, color: item.color }">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <p class="stat__value">{{ item.value }}</p>
            <p class="stat__label">{{ item.label }}</p>
          </div>
        </div>
      </el-col>
    </el-row>

    <section class="targets">
      <div class="targets__head">
        <div>
          <h3 class="targets__title">选择练习方向</h3>
          <p class="muted targets__sub">每个方向都准备了常见考点，难度从基础到进阶</p>
        </div>
        <el-button text type="primary" @click="goHistory">查看全部记录</el-button>
      </div>

      <el-alert
        v-if="targetsError"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
        :title="targetsError"
      >
        <template #default>
          <el-button link type="primary" size="small" @click="loadTargets">重新加载</el-button>
        </template>
      </el-alert>

      <el-row v-if="targetsLoading" :gutter="16">
        <el-col v-for="n in 6" :key="n" :xs="24" :sm="12" :lg="8">
          <div class="card target-card">
            <el-skeleton :rows="4" animated />
          </div>
        </el-col>
      </el-row>

      <EmptyState
        v-else-if="!targetsError && targets.length === 0"
        title="暂无可练习的方向"
        description="稍后再来看看，或先看看面试指南做准备"
        :icon="Reading"
      >
        <template #action>
          <el-button type="primary" @click="loadTargets">重新加载</el-button>
        </template>
      </EmptyState>

      <el-row v-else :gutter="16">
        <el-col v-for="target in targets" :key="target.id" :xs="24" :sm="12" :lg="8">
          <button type="button" class="card target-card" @click="goCreateWithTarget(target.id)">
            <div class="target-card__head">
              <span class="target-card__icon">
                <el-icon :size="22"><component :is="resolveIcon(target.icon)" /></el-icon>
              </span>
              <span class="target-card__category">{{ practiceCategoryLabel(target.category) }}</span>
            </div>
            <h4 class="target-card__title">{{ target.title }}</h4>
            <p class="target-card__subtitle">{{ target.subtitle || target.description || '来一场针对性练习' }}</p>
            <div class="target-card__topics">
              <el-tag
                v-for="topic in target.focusTopics.slice(0, 3)"
                :key="topic"
                size="small"
                effect="plain"
                type="info"
              >
                {{ topic }}
              </el-tag>
            </div>
            <div class="target-card__foot">
              <span class="target-card__rate">
                <el-rate :model-value="target.difficulty" disabled :max="5" size="small" />
              </span>
              <span class="target-card__count">默认 {{ target.defaultQuestions }} 题</span>
            </div>
            <el-icon class="target-card__arrow"><ArrowRight /></el-icon>
          </button>
        </el-col>
      </el-row>
    </section>

    <router-link :to="{ name: 'guide' }" class="guide-entry card">
      <div class="guide-entry__icon">
        <el-icon :size="24"><Reading /></el-icon>
      </div>
      <div class="guide-entry__main">
        <h4>第一次面试？先看指南</h4>
        <p class="muted">面试流程、准备清单、30 秒自我介绍模板、STAR 法则，五分钟看完心里有底</p>
      </div>
      <el-icon class="guide-entry__arrow"><ArrowRight /></el-icon>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChatDotRound,
  CircleCheck,
  Cpu,
  DataAnalysis,
  Goods,
  MagicStick,
  Monitor,
  Platform,
  Reading,
  Setting,
  Trophy,
  VideoCamera,
  VideoPlay,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { practiceCategoryLabel } from '@/utils/constants'
import EmptyState from '@/components/EmptyState.vue'
import { fetchMyPracticeSessions, fetchPracticeTargets } from '@/api/practice'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatScore } from '@/utils/format'
import type { PracticeSessionVO, PracticeTargetVO } from '@/types/practice'

const auth = useAuthStore()
const router = useRouter()

const sessionsLoading = ref(true)
const sessionsLoaded = ref(false)
const sessionsError = ref('')
const sessionsTotal = ref(0)
const finishedTotal = ref(0)
const recentScore = ref<number | undefined>()
const activeSession = ref<PracticeSessionVO | null>(null)

const targetsLoading = ref(true)
const targetsError = ref('')
const targets = ref<PracticeTargetVO[]>([])

const greeting = computed(() => {
  const hour = new Date().getHours()
  const prefix = hour < 6 ? '夜深了' : hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
  return `${prefix}，${auth.displayName}`
})

const progressPercent = computed(() => {
  const session = activeSession.value
  if (!session || session.totalQuestions <= 0) return 0
  return Math.min(100, Math.round((session.answeredCount / session.totalQuestions) * 100))
})

interface StatItem {
  label: string
  value: string | number
  icon: Component
  color: string
  bg: string
}

const stats = computed<StatItem[]>(() => [
  {
    label: '累计练习',
    value: sessionsTotal.value,
    icon: ChatDotRound,
    color: '#2f6bff',
    bg: '#eef3ff',
  },
  {
    label: '已完成',
    value: finishedTotal.value,
    icon: CircleCheck,
    color: '#1f9d55',
    bg: '#eaf7ef',
  },
  {
    label: '最近得分',
    value: formatScore(recentScore.value),
    icon: Trophy,
    color: '#d9822b',
    bg: '#fff5e8',
  },
])

/** 后端 icon 字段到 Element Plus 图标的映射，取不到时用 Reading 兜底 */
const ICON_MAP: Record<string, Component> = {
  Monitor,
  Platform,
  Goods,
  DataAnalysis,
  CircleCheck,
  Setting,
  Cpu,
  ChatDotRound,
}

function resolveIcon(icon?: string): Component {
  if (!icon) return Reading
  return ICON_MAP[icon] ?? Reading
}

async function loadSessions(): Promise<void> {
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    const [recent, finished] = await Promise.all([
      fetchMyPracticeSessions({ page: 1, size: 5 }),
      fetchMyPracticeSessions({ page: 1, size: 1, status: 'FINISHED' }),
    ])
    sessionsTotal.value = recent.total
    activeSession.value =
      recent.records.find((item) => item.status === 'CREATED' || item.status === 'IN_PROGRESS') ?? null
    finishedTotal.value = finished.total
    recentScore.value = finished.records[0]?.score
    sessionsLoaded.value = true
  } catch (error) {
    sessionsError.value = error instanceof ApiError ? error.message : '加载练习数据失败，请稍后重试'
  } finally {
    sessionsLoading.value = false
  }
}

async function loadTargets(): Promise<void> {
  targetsLoading.value = true
  targetsError.value = ''
  try {
    targets.value = await fetchPracticeTargets()
  } catch (error) {
    targetsError.value = error instanceof ApiError ? error.message : '加载练习方向失败，请稍后重试'
  } finally {
    targetsLoading.value = false
  }
}

function goCreate(): void {
  router.push({ name: 'practice-create' })
}

function goVideoLobby(): void {
  router.push({ name: 'video-lobby' })
}

function goCreateWithTarget(targetId: string): void {
  router.push({ name: 'practice-create', query: { targetId } })
}

function goRoom(id: string): void {
  router.push({ name: 'practice-room', params: { id } })
}

function goHistory(): void {
  router.push({ name: 'practice-history' })
}

onMounted(() => {
  loadSessions()
  loadTargets()
})
</script>

<style scoped lang="scss">
.continue {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ai-space-6);
  margin-bottom: var(--ai-space-4);
  padding: 26px 28px;
  color: #fff;
  border: none;
  background:
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.22), transparent 45%),
    linear-gradient(135deg, #2f6bff 0%, #4f8cff 55%, #6a93ff 100%);

  &__eyebrow {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    opacity: 0.9;
  }

  &__title {
    margin-top: 8px;
    font-size: 22px;
    font-weight: 600;
  }

  &__desc {
    margin-top: 6px;
    font-size: 13px;
    opacity: 0.85;
  }

  &__progress {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 16px;
    max-width: 420px;
  }

  &__progress :deep(.el-progress) {
    flex: 1;
  }

  &__progress-text {
    font-size: 12px;
    white-space: nowrap;
    opacity: 0.9;
  }

  &__button {
    flex-shrink: 0;
    color: #2f6bff;
    border-color: transparent;
    background: #fff;
  }

  &__button:hover {
    color: #2555cc;
    background: #f2f6ff;
  }
}

.stats {
  margin-bottom: var(--ai-space-4);
}

.stat {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  margin-bottom: var(--ai-space-4);

  &__icon {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__value {
    font-size: 24px;
    font-weight: 600;
    line-height: 1.2;
  }

  &__label {
    margin-top: 2px;
    font-size: 13px;
    color: var(--ai-text-secondary);
  }
}

.targets {
  &__head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: var(--ai-space-3);
    margin-bottom: var(--ai-space-3);
  }

  &__title {
    font-size: 17px;
    font-weight: 600;
  }

  &__sub {
    margin-top: 4px;
    font-size: 13px;
  }
}

.target-card {
  position: relative;
  width: 100%;
  margin-bottom: var(--ai-space-4);
  padding: 20px;
  text-align: left;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.18s ease;
  font: inherit;
  color: inherit;

  &:hover {
    border-color: var(--ai-primary);
    box-shadow: var(--ai-shadow-float);
    transform: translateY(-2px);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__icon {
    width: 42px;
    height: 42px;
    border-radius: 12px;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__category {
    font-size: 12px;
    color: var(--ai-text-placeholder);
  }

  &__title {
    margin-top: 12px;
    font-size: 16px;
    font-weight: 600;
  }

  &__subtitle {
    margin-top: 4px;
    min-height: 38px;
    font-size: 13px;
    line-height: 1.5;
    color: var(--ai-text-secondary);
    display: -webkit-box;
    overflow: hidden;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__topics {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 12px;
    min-height: 24px;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px dashed var(--ai-border);
  }

  &__rate :deep(.el-rate) {
    height: 18px;
  }

  &__count {
    font-size: 12px;
    color: var(--ai-text-placeholder);
  }

  &__arrow {
    position: absolute;
    right: 18px;
    top: 20px;
    color: var(--ai-text-placeholder);
    opacity: 0;
    transition: opacity 0.18s ease;
  }

  &:hover &__arrow {
    opacity: 1;
  }
}

.guide-entry {
  display: flex;
  align-items: center;
  gap: var(--ai-space-4);
  margin-top: var(--ai-space-2);
  color: inherit;
  transition: box-shadow 0.18s ease;

  &:hover {
    box-shadow: var(--ai-shadow-float);
  }

  &__icon {
    width: 52px;
    height: 52px;
    border-radius: 14px;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__main h4 {
    font-size: 15px;
    font-weight: 600;
  }

  &__main p {
    margin-top: 4px;
    font-size: 13px;
  }

  &__arrow {
    color: var(--ai-text-placeholder);
  }
}

@media (max-width: 768px) {
  .continue {
    flex-direction: column;
    align-items: stretch;
    gap: var(--ai-space-4);
    padding: 20px;
  }

  .continue__button {
    width: 100%;
  }

  .targets__head {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--ai-space-2);
  }

  .guide-entry {
    align-items: flex-start;
  }

  .guide-entry__main p {
    line-height: 1.6;
  }
}
</style>
