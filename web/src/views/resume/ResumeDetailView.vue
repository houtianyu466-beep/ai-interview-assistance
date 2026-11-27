<template>
  <div class="resume-detail">
    <PageHeader
      :title="detail?.resume.fileName || '简历详情'"
      :subtitle="detail ? `上传时间：${formatDateTime(detail.resume.createdAt)}` : ''"
      back
    >
      <template #actions>
        <StatusTag v-if="detail" kind="resume" :status="detail.resume.status" />
      </template>
    </PageHeader>

    <el-skeleton v-if="loading && !detail" :rows="10" animated class="card" />

    <template v-else-if="detail">
      <el-alert
        v-if="detail.resume.status === 'FAILED'"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
        :title="detail.resume.errorMessage || '简历解析失败，请重新上传'"
      />
      <el-alert
        v-else-if="detail.resume.status !== 'PARSED'"
        type="info"
        :closable="false"
        show-icon
        class="mb-4"
        title="AI 正在解析简历，页面会自动刷新，通常需要几秒钟"
      />

      <div v-if="detail.parsed" class="resume-detail__grid">
        <div class="card">
          <h3 class="card__title">基本信息</h3>
          <el-descriptions :column="2" border class="mt-3">
            <el-descriptions-item label="姓名">{{ detail.parsed.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="学历">{{ detail.parsed.education || '—' }}</el-descriptions-item>
            <el-descriptions-item label="工作年限">
              {{ detail.parsed.yearsOfExperience != null ? `${detail.parsed.yearsOfExperience} 年` : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ detail.parsed.email || '—' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.parsed.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="技能数">{{ detail.parsed.skills.length }}</el-descriptions-item>
          </el-descriptions>

          <template v-if="detail.parsed.summary">
            <h3 class="card__title mt-4">候选人概览</h3>
            <p class="pre-wrap muted mt-2">{{ detail.parsed.summary }}</p>
          </template>

          <h3 class="card__title mt-4">技能标签</h3>
          <div class="skill-tags">
            <el-tag v-for="skill in detail.parsed.skills" :key="skill" effect="plain">{{ skill }}</el-tag>
            <span v-if="detail.parsed.skills.length === 0" class="muted">未识别到技能</span>
          </div>

          <h3 class="card__title mt-4">亮点</h3>
          <ul class="highlight-list">
            <li v-for="highlight in detail.parsed.highlights" :key="highlight">{{ highlight }}</li>
            <li v-if="detail.parsed.highlights.length === 0" class="muted">暂无</li>
          </ul>
        </div>

        <div class="card">
          <h3 class="card__title">经历</h3>
          <el-timeline class="mt-3">
            <el-timeline-item
              v-for="(work, index) in detail.parsed.workExperiences"
              :key="`work-${index}`"
              :timestamp="work.period || ''"
              placement="top"
              type="primary"
            >
              <p class="timeline-title">{{ work.company || '未填写公司' }}</p>
              <p class="muted timeline-sub">{{ work.title || '未填写职位' }}</p>
              <p class="pre-wrap timeline-desc">{{ work.description || '' }}</p>
            </el-timeline-item>
            <el-timeline-item v-if="detail.parsed.workExperiences.length === 0" timestamp="—">
              暂无工作经历
            </el-timeline-item>
          </el-timeline>

          <el-divider />

          <h3 class="card__title">项目经历</h3>
          <el-timeline class="mt-3">
            <el-timeline-item
              v-for="(project, index) in detail.parsed.projects"
              :key="`project-${index}`"
              :timestamp="project.period || ''"
              placement="top"
              type="success"
            >
              <p class="timeline-title">{{ project.name || '未命名项目' }}</p>
              <p class="muted timeline-sub">{{ project.role || '' }}</p>
              <p class="pre-wrap timeline-desc">{{ project.description || '' }}</p>
            </el-timeline-item>
            <el-timeline-item v-if="detail.parsed.projects.length === 0" timestamp="—">
              暂无项目经历
            </el-timeline-item>
          </el-timeline>
        </div>

        <div class="card">
          <el-collapse>
            <el-collapse-item title="查看简历原文" name="raw">
              <pre class="raw-text">{{ detail.rawText || '（无原文）' }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { fetchResume } from '@/api/resumes'
import { ApiError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type { ResumeDetailVO } from '@/types/api'

const route = useRoute()
const router = useRouter()

const detail = ref<ResumeDetailVO | null>(null)
const loading = ref(false)
let timer: number | undefined
let attempts = 0

async function load(silent = false): Promise<void> {
  if (!silent) loading.value = true
  try {
    const result = await fetchResume(route.params.id as string)
    detail.value = result
    const terminal = result.resume.status === 'PARSED' || result.resume.status === 'FAILED'
    if (!terminal && !timer) {
      timer = window.setInterval(() => {
        attempts += 1
        if (attempts > 30) {
          stopPolling()
          return
        }
        load(true)
      }, 1200)
    }
    if (terminal) stopPolling()
  } catch (error) {
    if (!silent) {
      ElMessage.error(error instanceof ApiError ? error.message : '加载简历失败')
      router.replace({ name: 'resumes' })
    }
  } finally {
    loading.value = false
  }
}

function stopPolling(): void {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

onMounted(() => load())
onUnmounted(stopPolling)
</script>

<style scoped lang="scss">
.card__title {
  font-size: 15px;
  font-weight: 600;
}

.resume-detail {
  &__grid {
    display: grid;
    grid-template-columns: 1.15fr 0.85fr;
    gap: 16px;

    & > .card:last-child {
      grid-column: 1 / -1;
    }
  }
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.highlight-list {
  margin: 12px 0 0;
  padding-left: 18px;
  line-height: 2;
  color: var(--ai-text);
}

.timeline-title {
  font-weight: 600;
}

.timeline-sub {
  font-size: 12px;
  margin: 2px 0 6px;
}

.timeline-desc {
  font-size: 13px;
  color: var(--ai-text-secondary);
}

.raw-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ai-text-secondary);
  max-height: 420px;
  overflow: auto;
}

@media (max-width: 1100px) {
  .resume-detail__grid {
    grid-template-columns: 1fr;
  }
}
</style>
