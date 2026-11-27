<template>
  <section class="answer-feedback card">
    <div class="answer-feedback__head">
      <div>
        <p class="answer-feedback__eyebrow">本题得分</p>
        <p class="answer-feedback__score" :class="`is-${level}`">
          {{ formatScore(result.score) }}<small>分</small>
        </p>
      </div>
      <el-tag :type="tagType" effect="light" round>{{ tagLabel }}</el-tag>
    </div>

    <el-alert
      v-if="result.comment"
      class="answer-feedback__comment"
      type="info"
      :closable="false"
      show-icon
      title="AI 点评"
    >
      <p class="pre-wrap">{{ result.comment }}</p>
    </el-alert>

    <div class="answer-feedback__dimensions">
      <div v-for="dimension in dimensions" :key="dimension.key" class="dimension">
        <div class="dimension__head">
          <span class="dimension__label">{{ dimension.label }}</span>
          <strong class="dimension__value" :style="{ color: barColor(dimension.value) }">
            {{ formatScore(dimension.value) }}
          </strong>
        </div>
        <el-progress
          :percentage="clampPercent(dimension.value)"
          :stroke-width="7"
          :show-text="false"
          :color="barColor(dimension.value)"
        />
      </div>
    </div>

    <div v-if="hits.length || misses.length" class="answer-feedback__tags">
      <div v-if="hits.length" class="tag-group">
        <span class="tag-group__label">命中</span>
        <div class="tag-group__items">
          <el-tag v-for="hit in hits" :key="hit" type="success" effect="light" size="small">
            {{ hit }}
          </el-tag>
        </div>
      </div>
      <div v-if="misses.length" class="tag-group">
        <span class="tag-group__label">遗漏</span>
        <div class="tag-group__items">
          <el-tag v-for="miss in misses" :key="miss" type="danger" effect="light" size="small">
            {{ miss }}
          </el-tag>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatScore, scoreLevel } from '@/utils/format'
import type { AnswerResultVO } from '@/types/api'

const props = defineProps<{
  result: AnswerResultVO
}>()

const DIMENSION_LABELS: Record<string, string> = {
  accuracy: '准确性',
  depth: '深度',
  expression: '表达',
  structure: '结构',
}

const DIMENSION_ORDER = ['accuracy', 'depth', 'expression', 'structure']

const level = computed(() => scoreLevel(props.result.score))

const tagType = computed<'success' | 'warning' | 'danger'>(() => {
  if (level.value === 'high') return 'success'
  if (level.value === 'middle') return 'warning'
  return 'danger'
})

const tagLabel = computed(() => {
  if (level.value === 'high') return '表现出色'
  if (level.value === 'middle') return '基本合格'
  return '还需加强'
})

const hits = computed(() => props.result.hits ?? [])
const misses = computed(() => props.result.misses ?? [])

function normalizeScore(value: unknown): number {
  const score = Number(value)
  return Number.isFinite(score) ? score : 0
}

const dimensions = computed(() => {
  const scores = props.result.dimensionScores ?? {}
  // 已知四维固定展示；后端以后新增的维度按原始 key 追加，避免信息丢失
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

function clampPercent(value: number): number {
  return Math.min(100, Math.max(0, Math.round(value)))
}

function barColor(value: number): string {
  if (value < 60) return '#d64545'
  if (value < 80) return '#d9822b'
  return '#1f9d55'
}
</script>

<style scoped lang="scss">
.answer-feedback {
  padding: 20px;

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  &__eyebrow {
    font-size: 12px;
    letter-spacing: 0.08em;
    color: var(--ai-text-secondary);
  }

  &__score {
    margin-top: 2px;
    font-size: 36px;
    font-weight: 700;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;

    small {
      margin-left: 2px;
      font-size: 13px;
      font-weight: 400;
      color: var(--ai-text-secondary);
    }

    &.is-high {
      color: var(--ai-success);
    }

    &.is-middle {
      color: var(--ai-warning);
    }

    &.is-low {
      color: var(--ai-danger);
    }
  }

  &__comment {
    margin-top: 14px;
  }

  &__dimensions {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-top: 18px;
  }

  &__tags {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-top: 18px;
    padding-top: 14px;
    border-top: 1px dashed var(--ai-border);
  }
}

.dimension {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 6px;
    font-size: 13px;
  }

  &__label {
    color: var(--ai-text);
  }

  &__value {
    font-variant-numeric: tabular-nums;
  }
}

.tag-group {
  display: flex;
  align-items: flex-start;
  gap: 10px;

  &__label {
    flex-shrink: 0;
    padding-top: 2px;
    font-size: 12px;
    color: var(--ai-text-secondary);
  }

  &__items {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>
