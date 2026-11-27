<template>
  <div class="practice-review">
    <div
      v-if="loading"
      class="review-loading card"
      v-loading="true"
      element-loading-text="正在加载练习复盘…"
    />

    <div v-else-if="notFinished" class="review-state card">
      <el-result
        icon="warning"
        title="本次练习还未结束"
        sub-title="完成全部题目或点击「结束练习」后，才能查看本次复盘。"
      >
        <template #extra>
          <div class="review-state__actions">
            <el-button type="primary" @click="backToRoom">回到练习房间</el-button>
            <el-button @click="router.push({ name: 'practice-history' })">返回练习记录</el-button>
          </div>
        </template>
      </el-result>
    </div>

    <EmptyState
      v-else-if="loadError"
      title="复盘加载失败"
      :description="loadError"
      :icon="Warning"
    >
      <template #action>
        <div class="review-state__actions">
          <el-button type="primary" @click="load">重试</el-button>
          <el-button @click="router.push({ name: 'practice-history' })">返回练习记录</el-button>
        </div>
      </template>
    </EmptyState>

    <template v-else-if="review">
      <PageHeader :title="targetTitle" :subtitle="headerSubtitle">
        <template #actions>
          <div class="review-actions no-print">
            <el-button :icon="RefreshLeft" @click="practiceAgain">再练一次</el-button>
            <el-button :icon="HomeFilled" @click="router.push({ name: 'dashboard' })">返回首页</el-button>
            <el-button type="primary" :icon="Printer" @click="printReview">打印 / 导出 PDF</el-button>
          </div>
        </template>
      </PageHeader>

      <section class="review-hero card">
        <ScoreRing :score="review.overallScore" :size="168" label="总分" />
        <div class="review-hero__body">
          <div class="review-hero__badges">
            <span class="level-badge" :class="levelClass">{{ levelText }}</span>
            <span v-if="review.session.targetSubtitle" class="review-hero__subtitle">
              {{ review.session.targetSubtitle }}
            </span>
          </div>
          <p class="review-hero__summary">
            {{ review.summary || '完成一次练习就是进步。结合维度分和逐题参考答案，找出下一次最想改进的一个点。' }}
          </p>
          <div class="review-hero__stats">
            <span>{{ modeLabel(review.session.mode) }}</span>
            <span>共 {{ questions.length }} 题</span>
            <span>有效作答 {{ answeredCount }} 题</span>
            <span>用时 {{ formatDuration(review.session.durationSeconds) }}</span>
          </div>
        </div>
      </section>

      <section class="review-section card">
        <div class="section-head">
          <h2 class="section-head__title">能力维度</h2>
          <span class="muted">四维得分帮助你定位下一次的练习重点</span>
        </div>
        <div class="dimension-grid">
          <div v-for="dimension in dimensions" :key="dimension.key" class="dimension-card">
            <div class="dimension-card__head">
              <span class="dimension-card__label">{{ dimension.label }}</span>
              <strong class="dimension-card__score" :style="{ color: barColor(dimension.value) }">
                {{ formatScore(dimension.value) }}
              </strong>
            </div>
            <el-progress
              :percentage="clampPercent(dimension.value)"
              :stroke-width="10"
              :show-text="false"
              :color="barColor(dimension.value)"
            />
          </div>
        </div>
      </section>

      <div class="review-columns">
        <section class="review-section card">
          <div class="section-head">
            <h2 class="section-head__title">
              <el-icon class="is-success"><CircleCheck /></el-icon>
              做得好的地方
            </h2>
          </div>
          <ul v-if="strengths.length" class="insight-list">
            <li v-for="item in strengths" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="muted">暂时没有记录，继续练习后这里会越来越具体。</p>
        </section>

        <section class="review-section card">
          <div class="section-head">
            <h2 class="section-head__title">
              <el-icon class="is-warning"><Warning /></el-icon>
              可以提升的地方
            </h2>
          </div>
          <ul v-if="weaknesses.length" class="insight-list">
            <li v-for="item in weaknesses" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="muted">这次没有明显的短板，保持练习节奏就好。</p>
        </section>
      </div>

      <section class="review-section card">
        <div class="section-head">
          <h2 class="section-head__title">下一步练习建议</h2>
        </div>
        <ol v-if="nextSteps.length" class="next-steps">
          <li v-for="(step, index) in nextSteps" :key="`${index}-${step}`" class="next-steps__item">
            <span class="next-steps__index">{{ index + 1 }}</span>
            <span class="next-steps__text">{{ step }}</span>
          </li>
        </ol>
        <p v-else class="muted">完成更多练习后，这里会给出更具体的下一步建议。</p>
      </section>

      <section class="review-section card">
        <div class="section-head">
          <h2 class="section-head__title">逐题回顾</h2>
          <span class="muted">共 {{ questions.length }} 题 · 展开可查看我的回答与参考答案</span>
        </div>

        <el-collapse v-if="questions.length" v-model="activeNames" class="review-collapse">
          <el-collapse-item
            v-for="item in questions"
            :key="item.seq"
            :name="String(item.seq)"
          >
            <template #title>
              <div class="q-title">
                <span class="q-title__seq">第 {{ item.seq }} 题</span>
                <span class="q-title__content">{{ item.content }}</span>
                <span class="q-title__score" :class="questionScoreClass(item)">
                  {{ item.score != null ? `${formatScore(item.score)} 分` : '未作答' }}
                </span>
              </div>
            </template>

            <div class="q-review">
              <div class="q-review__block">
                <h3>我的回答</h3>
                <p v-if="item.answer" class="pre-wrap">{{ item.answer }}</p>
                <p v-else class="muted">本题未作答。</p>
              </div>

              <div v-if="questionDimensions(item).length" class="q-metrics">
                <div
                  v-for="dimension in questionDimensions(item)"
                  :key="dimension.key"
                  class="q-metric"
                >
                  <span class="muted">{{ dimension.label }}</span>
                  <strong :style="{ color: barColor(dimension.value) }">
                    {{ formatScore(dimension.value) }}
                  </strong>
                </div>
              </div>

              <div class="q-review__block">
                <h3>AI 点评</h3>
                <p v-if="item.comment" class="pre-wrap">{{ item.comment }}</p>
                <p v-else class="muted">暂无点评。</p>
              </div>

              <div class="q-review__block q-review__block--reference">
                <h3>参考答案</h3>
                <p v-if="item.referenceAnswer" class="pre-wrap">{{ item.referenceAnswer }}</p>
                <p v-else class="muted">暂无参考答案。</p>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <p v-else class="muted">暂无逐题记录。</p>
      </section>
    </template>

    <EmptyState
      v-else
      title="复盘不存在"
      description="没有找到这次练习的复盘记录，请回到练习记录重新选择。"
      :icon="Warning"
    >
      <template #action>
        <el-button type="primary" @click="router.push({ name: 'practice-history' })">
          返回练习记录
        </el-button>
      </template>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  HomeFilled,
  Printer,
  RefreshLeft,
  Warning,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ScoreRing from '@/components/practice/ScoreRing.vue'
import { fetchPracticeReview } from '@/api/practice'
import { ApiError } from '@/api/http'
import { INTERVIEW_MODE_META } from '@/utils/constants'
import { formatDateTime, formatDuration, formatScore, scoreLevel } from '@/utils/format'
import type { PracticeLevel, PracticeQuestionReviewVO, PracticeReviewVO } from '@/types/practice'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const review = ref<PracticeReviewVO | null>(null)
const notFinished = ref(false)
const loadError = ref('')
const activeNames = ref<Array<string | number>>([])

const sessionId = computed(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? raw[0] ?? '' : String(raw ?? '')
})

const DIMENSION_LABELS: Record<string, string> = {
  accuracy: '准确性',
  depth: '深度',
  expression: '表达',
  structure: '结构',
}

const DIMENSION_ORDER = ['accuracy', 'depth', 'expression', 'structure']

const LEVEL_META: Record<PracticeLevel, { label: string; className: string }> = {
  EXCELLENT: { label: '优秀', className: 'is-excellent' },
  GOOD: { label: '良好', className: 'is-good' },
  IMPROVING: { label: '待提升', className: 'is-improving' },
  NEEDS_WORK: { label: '需加强', className: 'is-needs-work' },
}

const questions = computed(() => review.value?.questions ?? [])
const strengths = computed(() => review.value?.strengths ?? [])
const weaknesses = computed(() => review.value?.weaknesses ?? [])
const nextSteps = computed(() => review.value?.nextSteps ?? [])

const answeredCount = computed(
  () => questions.value.filter((item) => Boolean(item.answer?.trim())).length,
)

const levelKey = computed<PracticeLevel>(() => {
  const level = review.value?.level
  if (level && LEVEL_META[level]) return level
  const score = Number(review.value?.overallScore ?? 0)
  if (score >= 85) return 'EXCELLENT'
  if (score >= 70) return 'GOOD'
  if (score >= 55) return 'IMPROVING'
  return 'NEEDS_WORK'
})

const levelText = computed(() => review.value?.levelLabel || LEVEL_META[levelKey.value].label)
const levelClass = computed(() => LEVEL_META[levelKey.value].className)

const targetTitle = computed(() => review.value?.session.targetTitle || '练习复盘')

const headerSubtitle = computed(() => {
  const session = review.value?.session
  if (!session) return ''
  const parts: string[] = []
  if (session.finishedAt) {
    parts.push(`完成时间：${formatDateTime(session.finishedAt)}`)
  } else if (session.createdAt) {
    parts.push(`创建时间：${formatDateTime(session.createdAt)}`)
  }
  parts.push(`用时 ${formatDuration(session.durationSeconds)}`)
  parts.push(modeLabel(session.mode))
  return parts.join(' · ')
})

const dimensions = computed(() => {
  const scores = review.value?.dimensionScores ?? {}
  const known = DIMENSION_ORDER.map((key) => ({
    key,
    label: DIMENSION_LABELS[key],
    value: normalizeScore(scores[key]),
  }))
  const extra = Object.keys(scores)
    .filter((key) => !DIMENSION_ORDER.includes(key))
    .map((key) => ({
      key,
      label: key,
      value: normalizeScore(scores[key]),
    }))
  return [...known, ...extra]
})

function normalizeScore(value: unknown): number {
  const score = Number(value)
  return Number.isFinite(score) ? score : 0
}

function clampPercent(value: number): number {
  return Math.min(100, Math.max(0, Math.round(value)))
}

function barColor(value: number): string {
  if (value < 60) return '#d64545'
  if (value < 80) return '#d9822b'
  return '#1f9d55'
}

function modeLabel(mode?: string): string {
  if (!mode) return '练习'
  return INTERVIEW_MODE_META[mode as keyof typeof INTERVIEW_MODE_META] ?? mode
}

function questionScoreClass(item: PracticeQuestionReviewVO): string {
  if (item.score == null) return 'is-empty'
  return `is-${scoreLevel(item.score)}`
}

function questionDimensions(item: PracticeQuestionReviewVO) {
  const scores = item.dimensionScores ?? {}
  const known = DIMENSION_ORDER.filter((key) => scores[key] != null).map((key) => ({
    key,
    label: DIMENSION_LABELS[key],
    value: normalizeScore(scores[key]),
  }))
  const extra = Object.keys(scores)
    .filter((key) => !DIMENSION_ORDER.includes(key))
    .map((key) => ({
      key,
      label: key,
      value: normalizeScore(scores[key]),
    }))
  return [...known, ...extra]
}

async function load(): Promise<void> {
  const id = sessionId.value
  review.value = null
  notFinished.value = false
  loadError.value = ''
  activeNames.value = []

  if (!id) {
    loading.value = false
    loadError.value = '缺少练习 ID，请从练习记录重新进入'
    return
  }

  loading.value = true
  try {
    review.value = await fetchPracticeReview(id)
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      notFinished.value = true
      ElMessage.warning('本次练习还未结束，完成后再查看复盘')
    } else if (error instanceof ApiError && error.status === 404) {
      ElMessage.error('未找到本次练习复盘，已返回练习记录')
      await router.replace({ name: 'practice-history' })
    } else {
      loadError.value = error instanceof ApiError ? error.message : '复盘加载失败，请稍后重试'
      ElMessage.error(loadError.value)
    }
  } finally {
    loading.value = false
  }
}

function backToRoom(): void {
  void router.push({ name: 'practice-room', params: { id: sessionId.value } })
}

function practiceAgain(): void {
  const targetId = review.value?.session.targetId
  void router.push({
    name: 'practice-create',
    query: targetId ? { targetId } : {},
  })
}

async function printReview(): Promise<void> {
  const previous = [...activeNames.value]
  activeNames.value = questions.value.map((item) => String(item.seq))
  await nextTick()
  window.print()
  activeNames.value = previous
}

watch(
  () => route.params.id,
  (next, previous) => {
    if (next !== previous) void load()
  },
)

onMounted(() => {
  void load()
})
</script>

<style scoped lang="scss">
.practice-review {
  max-width: 1100px;
  margin: 0 auto;
}

.review-loading {
  min-height: 360px;
}

.review-state {
  padding: 12px;

  &__actions {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.review-hero {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 28px 32px;
  background:
    radial-gradient(circle at 92% 12%, rgba(47, 107, 255, 0.09), transparent 36%),
    var(--ai-bg-card);

  &__body {
    min-width: 0;
    flex: 1;
  }

  &__badges {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
  }

  &__subtitle {
    font-size: 13px;
    color: var(--ai-text-secondary);
  }

  &__summary {
    margin-top: 14px;
    font-size: 14px;
    line-height: 1.9;
    color: var(--ai-text);
  }

  &__stats {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 18px;
    margin-top: 16px;
    font-size: 12px;
    color: var(--ai-text-secondary);
  }
}

.level-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  print-color-adjust: exact;
  -webkit-print-color-adjust: exact;

  &.is-excellent {
    border-color: #bfe8cf;
    background: #e8f7ee;
    color: var(--ai-success);
  }

  &.is-good {
    border-color: #c9d8ff;
    background: #eef3ff;
    color: var(--ai-primary);
  }

  &.is-improving {
    border-color: #f2d3ae;
    background: #fdf3e8;
    color: var(--ai-warning);
  }

  &.is-needs-work {
    border-color: #f4c6c6;
    background: #fdecec;
    color: var(--ai-danger);
  }
}

.review-section {
  margin-top: 16px;
  padding: 22px 24px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  &__title {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
  }
}

.is-success {
  color: var(--ai-success);
}

.is-warning {
  color: var(--ai-warning);
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 28px;
  margin-top: 18px;
}

.dimension-card {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 7px;
    font-size: 13px;
  }

  &__score {
    font-variant-numeric: tabular-nums;
  }
}

.review-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  .review-section {
    margin-top: 16px;
  }
}

.insight-list {
  margin: 14px 0 0;
  padding-left: 18px;
  color: var(--ai-text);
  font-size: 13px;
  line-height: 1.9;

  li + li {
    margin-top: 6px;
  }
}

.next-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;

  &__item {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px 14px;
    border-radius: var(--ai-radius-md);
    background: var(--ai-bg);
    font-size: 13px;
    line-height: 1.8;
  }

  &__index {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    margin-top: 2px;
    border-radius: 50%;
    background: var(--ai-primary);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
  }

  &__text {
    min-width: 0;
  }
}

.review-collapse {
  margin-top: 14px;
  border-top: 1px solid var(--ai-border);

  :deep(.el-collapse-item__header) {
    height: auto;
    min-height: 52px;
    line-height: 1.6;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 20px;
  }
}

.q-title {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 6px 12px 6px 0;

  &__seq {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 12px;
  }

  &__content {
    min-width: 0;
    flex: 1;
    font-size: 13px;
    font-weight: 500;
    line-height: 1.6;
    color: var(--ai-text);
    overflow-wrap: anywhere;
    white-space: normal;
  }

  &__score {
    flex-shrink: 0;
    font-size: 12px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;

    &.is-high {
      color: var(--ai-success);
    }

    &.is-middle {
      color: var(--ai-warning);
    }

    &.is-low {
      color: var(--ai-danger);
    }

    &.is-empty {
      color: var(--ai-text-secondary);
    }
  }
}

.q-review {
  &__block {
    padding: 14px 16px;
    border-radius: var(--ai-radius-md);
    background: var(--ai-bg);
    font-size: 13px;

    h3 {
      margin-bottom: 8px;
      font-size: 13px;
      font-weight: 600;
      color: var(--ai-text-secondary);
    }

    & + & {
      margin-top: 12px;
    }
  }

  &__block--reference {
    border-left: 3px solid #e8bd6b;
    background: #fff8e8;
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }
}

.q-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.q-metric {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border: 1px solid var(--ai-border);
  border-radius: 999px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .practice-review {
    :deep(.page-header) {
      align-items: stretch;
      flex-direction: column;
    }
  }

  .review-actions {
    width: 100%;

    :deep(.el-button) {
      flex: 1;
    }
  }

  .review-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 20px;
    padding: 22px;
  }

  .dimension-grid,
  .review-columns {
    grid-template-columns: 1fr;
  }

  .review-section {
    padding: 18px;
  }

  .q-title {
    gap: 8px;
  }
}

@media print {
  @page {
    margin: 12mm;
  }

  :global(.shell) {
    height: auto !important;
  }

  :global(.shell__aside),
  :global(.shell__header) {
    display: none !important;
  }

  :global(.shell__body) {
    width: 100% !important;
  }

  :global(.shell__main) {
    padding: 0 !important;
    overflow: visible !important;
    background: #fff !important;
  }

  :global(.el-progress-bar__inner) {
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }

  .practice-review {
    max-width: none;
  }

  .no-print {
    display: none !important;
  }

  .card {
    box-shadow: none !important;
    border: 1px solid var(--ai-border);
  }

  .review-hero {
    background: #fff !important;
  }

  .review-section {
    margin-top: 12px;
    break-inside: avoid;
  }

  .review-collapse {
    :deep(.el-collapse-item) {
      break-inside: avoid;
    }
  }
}
</style>
