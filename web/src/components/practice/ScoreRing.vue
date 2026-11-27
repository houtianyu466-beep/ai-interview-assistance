<template>
  <div class="score-ring" :style="ringStyle" role="img" :aria-label="ariaLabel">
    <div class="score-ring__inner">
      <span class="score-ring__value">{{ formattedScore }}</span>
      <span v-if="label" class="score-ring__label">{{ label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatScore } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    score: number
    size?: number
    label?: string
  }>(),
  {
    size: 140,
    label: '',
  },
)

const clampedScore = computed(() => {
  const value = Number(props.score)
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, value))
})

const formattedScore = computed(() => formatScore(clampedScore.value))

/** <60 红、60-79 橙、>=80 绿 */
const color = computed(() => {
  if (clampedScore.value < 60) return '#d64545'
  if (clampedScore.value < 80) return '#d9822b'
  return '#1f9d55'
})

const ariaLabel = computed(() => `${props.label || '得分'} ${formattedScore.value} 分`)

const ringStyle = computed(
  () =>
    `--ring-size:${props.size}px;--ring-value:${clampedScore.value}%;--ring-color:${color.value};`,
)
</script>

<style scoped lang="scss">
.score-ring {
  --ring-size: 140px;
  --ring-value: 0%;
  --ring-color: var(--ai-danger);
  --ring-thickness: max(9px, calc(var(--ring-size) * 0.09));

  display: grid;
  place-items: center;
  width: var(--ring-size);
  height: var(--ring-size);
  flex-shrink: 0;
  border-radius: 50%;
  background: conic-gradient(var(--ring-color) var(--ring-value), #e9edf2 0);
  print-color-adjust: exact;
  -webkit-print-color-adjust: exact;

  &__inner {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: calc(var(--ring-size) - var(--ring-thickness) * 2);
    height: calc(var(--ring-size) - var(--ring-thickness) * 2);
    border-radius: 50%;
    background: var(--ai-bg-card);
    box-shadow: inset 0 0 0 1px rgba(31, 35, 41, 0.04);
  }

  &__value {
    font-size: calc(var(--ring-size) * 0.26);
    font-weight: 700;
    line-height: 1.1;
    color: var(--ring-color);
    font-variant-numeric: tabular-nums;
  }

  &__label {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ai-text-secondary);
  }
}

@media print {
  .score-ring {
    box-shadow: none;

    &__inner {
      background: #fff;
    }
  }
}
</style>
