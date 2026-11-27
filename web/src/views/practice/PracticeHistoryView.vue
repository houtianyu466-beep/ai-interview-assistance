<template>
  <div class="history">
    <PageHeader title="练习记录" subtitle="每一场练习都会留下复盘，方便你看到自己的进步">
      <template #actions>
        <el-button type="primary" :icon="VideoPlay" @click="goCreate">开始练习</el-button>
      </template>
    </PageHeader>

    <div class="card">
      <div class="history__toolbar">
        <el-radio-group v-model="filter" @change="onFilterChange">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="IN_PROGRESS">进行中</el-radio-button>
          <el-radio-button value="FINISHED">已完成</el-radio-button>
        </el-radio-group>
        <span class="muted">共 {{ total }} 场练习</span>
      </div>

      <el-alert
        v-if="error"
        type="error"
        :closable="false"
        show-icon
        :title="error"
      >
        <template #default>
          <el-button link type="primary" size="small" @click="load">重新加载</el-button>
        </template>
      </el-alert>

      <el-skeleton v-else-if="loading && records.length === 0" :rows="6" animated />

      <EmptyState
        v-else-if="!loading && records.length === 0"
        title="还没有练习记录"
        description="选一个方向，开始你的第一次模拟面试吧"
        :icon="Clock"
      >
        <template #action>
          <el-button type="primary" @click="goCreate">开始第一次练习</el-button>
        </template>
      </EmptyState>

      <template v-else>
        <div class="history__scroll">
          <div v-loading="loading" class="history__list">
            <div class="history__head">
              <span>方向</span>
              <span>模式</span>
              <span>状态</span>
              <span>进度</span>
              <span>得分</span>
              <span>创建时间</span>
              <span>操作</span>
            </div>

            <div v-for="row in records" :key="row.id" class="history-row">
              <div class="history-row__target">
                <strong>{{ row.targetTitle }}</strong>
                <small v-if="row.targetSubtitle">{{ row.targetSubtitle }}</small>
              </div>

              <div class="history-cell">
                <span class="history-cell__label">模式</span>
                <span>{{ modeLabel(row.mode) }}</span>
              </div>

              <div class="history-cell">
                <span class="history-cell__label">状态</span>
                <StatusTag kind="interview" :status="row.status" />
              </div>

              <div class="history-cell">
                <span class="history-cell__label">进度</span>
                <span>{{ row.currentIndex }} / {{ row.totalQuestions }} 题</span>
              </div>

              <div class="history-cell">
                <span class="history-cell__label">得分</span>
                <span :class="scoreClass(row.score)">{{ formatScore(row.score) }}</span>
              </div>

              <div class="history-cell">
                <span class="history-cell__label">创建时间</span>
                <span>{{ formatDateTime(row.createdAt) }}</span>
              </div>

              <div class="history-row__actions">
                <el-button
                  v-if="row.status === 'CREATED' || row.status === 'IN_PROGRESS'"
                  link
                  type="primary"
                  @click="goRoom(row.id)"
                >
                  继续练习
                </el-button>
                <el-button
                  v-else-if="row.status === 'FINISHED'"
                  link
                  type="primary"
                  @click="goReview(row.id)"
                >
                  查看复盘
                </el-button>
                <span v-else class="muted">—</span>
              </div>
            </div>
          </div>
        </div>

        <div class="history__pagination">
          <el-pagination
            v-model:current-page="page"
            background
            layout="total, prev, pager, next"
            :page-size="size"
            :total="total"
            @current-change="load"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, VideoPlay } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { fetchMyPracticeSessions } from '@/api/practice'
import { ApiError } from '@/api/http'
import { INTERVIEW_MODE_META, type InterviewMode } from '@/utils/constants'
import { formatDateTime, formatScore } from '@/utils/format'
import type { PracticeSessionVO } from '@/types/practice'

type FilterValue = 'ALL' | 'IN_PROGRESS' | 'FINISHED'

const router = useRouter()

const filter = ref<FilterValue>('ALL')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const records = ref<PracticeSessionVO[]>([])
const loading = ref(true)
const error = ref('')

function modeLabel(mode: string): string {
  return INTERVIEW_MODE_META[mode as InterviewMode] ?? mode ?? '—'
}

function scoreClass(score?: number): string {
  if (score == null) return 'muted'
  if (score >= 80) return 'text-success'
  if (score >= 60) return 'history-row__score--mid'
  return 'text-danger'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchMyPracticeSessions({
      page: page.value,
      size: size.value,
      status: filter.value === 'ALL' ? undefined : filter.value,
    })
    records.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '加载练习记录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onFilterChange(): void {
  page.value = 1
  load()
}

function goCreate(): void {
  router.push({ name: 'practice-create' })
}

function goRoom(id: string): void {
  router.push({ name: 'practice-room', params: { id } })
}

function goReview(id: string): void {
  router.push({ name: 'practice-review', params: { id } })
}

onMounted(load)
</script>

<style scoped lang="scss">
.history {
  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ai-space-3);
    margin-bottom: var(--ai-space-4);
    flex-wrap: wrap;
  }

  &__scroll {
    overflow-x: auto;
  }

  &__list {
    min-width: 760px;
  }

  &__head {
    display: grid;
    grid-template-columns: minmax(150px, 1.7fr) 75px 95px 95px 70px 145px 120px;
    gap: var(--ai-space-3);
    padding: 10px 12px;
    font-size: 12px;
    color: var(--ai-text-secondary);
    background: var(--ai-bg);
    border-radius: var(--ai-radius-sm);
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: var(--ai-space-4);
  }
}

.history-row {
  display: grid;
  grid-template-columns: minmax(150px, 1.7fr) 75px 95px 95px 70px 145px 120px;
  gap: var(--ai-space-3);
  align-items: center;
  padding: 14px 12px;
  border-bottom: 1px solid var(--ai-border);
  transition: background 0.15s ease;

  &:hover {
    background: #fafbfc;
  }

  &:last-child {
    border-bottom: none;
  }

  &__target {
    display: flex;
    flex-direction: column;
    min-width: 0;

    strong {
      font-size: 14px;
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    small {
      margin-top: 2px;
      font-size: 12px;
      color: var(--ai-text-secondary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &__score--mid {
    color: var(--ai-warning);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
  }
}

.history-cell {
  font-size: 13px;

  &__label {
    display: none;
    margin-right: var(--ai-space-2);
    font-size: 12px;
    color: var(--ai-text-secondary);
  }
}

@media (max-width: 768px) {
  .history__scroll {
    overflow-x: visible;
  }

  .history__list {
    min-width: 0;
  }

  .history__head {
    display: none;
  }

  .history-row {
    grid-template-columns: 1fr;
    gap: 6px;
    padding: 14px 10px;
    border: 1px solid var(--ai-border);
    border-radius: var(--ai-radius-md);
    margin-bottom: var(--ai-space-3);

    &__target {
      margin-bottom: 4px;
    }

    &__actions {
      margin-top: var(--ai-space-2);
      padding-top: var(--ai-space-2);
      border-top: 1px dashed var(--ai-border);
    }
  }

  .history-cell {
    display: flex;
    align-items: center;
    justify-content: space-between;

    &__label {
      display: inline;
    }
  }

  .history__pagination {
    justify-content: center;
  }
}
</style>
