<template>
  <el-tag :type="meta.type" effect="light" round size="small">{{ meta.label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  INTERVIEW_STATUS_META,
  JOB_STATUS_META,
  RECOMMENDATION_META,
  RESUME_STATUS_META,
  ROLE_META,
  type TagMeta,
} from '@/utils/constants'

const props = defineProps<{
  kind: 'job' | 'resume' | 'interview' | 'role' | 'recommendation'
  status?: string | null
}>()

const fallback: TagMeta = { label: '未知', type: 'info' }

const meta = computed<TagMeta>(() => {
  const status = props.status ?? ''
  switch (props.kind) {
    case 'job':
      return JOB_STATUS_META[status as keyof typeof JOB_STATUS_META] ?? { ...fallback, label: status || '未知' }
    case 'resume':
      return RESUME_STATUS_META[status as keyof typeof RESUME_STATUS_META] ?? { ...fallback, label: status || '未知' }
    case 'interview':
      return INTERVIEW_STATUS_META[status as keyof typeof INTERVIEW_STATUS_META] ?? { ...fallback, label: status || '未知' }
    case 'role':
      return ROLE_META[status as keyof typeof ROLE_META] ?? { ...fallback, label: status || '未知' }
    case 'recommendation':
      return RECOMMENDATION_META[status as keyof typeof RECOMMENDATION_META] ?? { ...fallback, label: status || '未知' }
    default:
      return fallback
  }
})
</script>
