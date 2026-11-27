<template>
  <div class="resume-list">
    <PageHeader
      title="简历中心"
      subtitle="上传 docx / pdf 或粘贴文本，AI 会自动解析学历、技能、项目与亮点"
    >
      <template #actions>
        <el-button :icon="EditPen" @click="openPaste">粘贴文本</el-button>
      </template>
    </PageHeader>

    <div class="card upload-card">
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        :disabled="uploading"
        accept=".txt,.md,.docx,.pdf"
        :on-change="onFileChange"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把简历拖到这里，或 <em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 txt / md / docx / pdf，单个文件不超过 10MB；上传后自动开始解析</div>
        </template>
      </el-upload>
      <el-progress v-if="uploading" :percentage="uploadProgress" :stroke-width="8" class="mt-3" />
    </div>

    <div v-loading="loading" class="resume-grid">
      <div v-for="resume in list" :key="resume.id" class="resume-card card">
        <div class="resume-card__head">
          <div class="resume-card__title">
            <el-icon class="resume-card__icon"><Document /></el-icon>
            <div class="resume-card__name-wrap">
              <p class="resume-card__name">{{ resume.fileName || '未命名简历' }}</p>
              <p class="muted resume-card__meta">
                {{ resume.education || '学历未知' }} ·
                {{ resume.yearsOfExperience != null ? `${resume.yearsOfExperience} 年经验` : '年限未知' }} ·
                {{ formatDateTime(resume.createdAt) }}
              </p>
            </div>
          </div>
          <div class="flex-center gap-2">
            <el-icon v-if="resume.status === 'PARSING'" class="is-loading"><Loading /></el-icon>
            <StatusTag kind="resume" :status="resume.status" />
          </div>
        </div>

        <div v-if="resume.status === 'FAILED'" class="resume-card__error">
          <el-alert
            type="error"
            :closable="false"
            show-icon
            :title="resume.errorMessage || '解析失败，请重新上传'"
          />
        </div>

        <div v-else class="resume-card__skills">
          <template v-if="splitSkills(resume.skills).length">
            <el-tag
              v-for="skill in splitSkills(resume.skills).slice(0, 8)"
              :key="skill"
              size="small"
              type="info"
              effect="plain"
            >
              {{ skill }}
            </el-tag>
            <span v-if="splitSkills(resume.skills).length > 8" class="muted resume-card__more">
              +{{ splitSkills(resume.skills).length - 8 }}
            </span>
          </template>
          <span v-else class="muted">解析完成后展示技能标签</span>
        </div>

        <div class="resume-card__actions">
          <el-button text type="primary" @click="router.push({ name: 'resume-detail', params: { id: resume.id } })">
            查看详情
          </el-button>
          <el-button text type="danger" @click="remove(resume)">删除</el-button>
        </div>
      </div>
    </div>

    <EmptyState
      v-if="!loading && list.length === 0"
      title="还没有简历"
      description="上传 docx / pdf、粘贴文本，或使用演示账号 candidate 自带的简历"
      :icon="Document"
    >
      <template #action>
        <el-button type="primary" @click="openPaste">粘贴文本创建</el-button>
      </template>
    </EmptyState>

    <el-dialog v-model="pasteVisible" title="粘贴简历文本" width="660px" :close-on-click-modal="false">
      <el-input
        v-model="pasteContent"
        type="textarea"
        :rows="12"
        maxlength="20000"
        show-word-limit
        placeholder="把简历内容粘贴到这里，例如：&#10;张伟，本科，5 年 Java 后端经验，熟悉 Spring Boot、MySQL、Redis……"
      />
      <template #footer>
        <el-button @click="pasteVisible = false">取消</el-button>
        <el-button type="primary" :loading="pasteSubmitting" :disabled="!pasteContent.trim()" @click="submitPaste">
          创建并解析
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="matchVisible" title="岗位匹配度" width="580px" @closed="resetMatch">
      <el-form label-width="80px">
        <el-form-item label="目标岗位">
          <el-select v-model="matchJobId" placeholder="选择一个招聘中的岗位" class="w-full" :loading="matchJobsLoading">
            <el-option
              v-for="job in matchJobs"
              :key="job.id"
              :label="`${job.title}${job.department ? ' · ' + job.department : ''}`"
              :value="job.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="flex-center gap-2">
        <el-button type="primary" :loading="matchLoading" :disabled="!matchJobId" @click="runMatch">
          开始匹配
        </el-button>
        <span class="muted">由 AI 对比简历与岗位要求，给出匹配分与技能差距</span>
      </div>

      <div v-if="matchResult" class="match-result">
        <div class="match-result__score" :class="`is-${scoreLevel(matchResult.score)}`">
          {{ formatScore(matchResult.score) }}
          <small>分</small>
        </div>
        <div class="match-result__body">
          <p class="mb-2"><strong>已具备</strong></p>
          <div class="match-result__tags">
            <el-tag v-for="skill in matchResult.matchedSkills" :key="skill" type="success" size="small" effect="light">
              {{ skill }}
            </el-tag>
            <span v-if="!matchResult.matchedSkills.length" class="muted">暂无命中技能</span>
          </div>
          <p class="mb-2 mt-3"><strong>仍需补充</strong></p>
          <div class="match-result__tags">
            <el-tag v-for="skill in matchResult.missingSkills" :key="skill" type="warning" size="small" effect="light">
              {{ skill }}
            </el-tag>
            <span v-if="!matchResult.missingSkills.length" class="muted">无明显缺口</span>
          </div>
          <el-alert
            v-if="matchResult.advice"
            class="mt-3"
            type="info"
            :closable="false"
            show-icon
            :title="matchResult.advice"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Document, EditPen, Loading, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  createResumeFromText,
  deleteResume,
  fetchMyResumes,
  fetchResume,
  matchResume,
  uploadResume,
} from '@/api/resumes'
import { fetchJobs } from '@/api/jobs'
import { ApiError } from '@/api/http'
import { formatDateTime, formatScore, scoreLevel, splitSkills } from '@/utils/format'
import type { JobVO, MatchVO, ResumeVO } from '@/types/api'

const router = useRouter()

const list = ref<ResumeVO[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)

const pasteVisible = ref(false)
const pasteContent = ref('')
const pasteSubmitting = ref(false)

const matchVisible = ref(false)
const matchTarget = ref<ResumeVO | null>(null)
const matchJobs = ref<JobVO[]>([])
const matchJobsLoading = ref(false)
const matchJobId = ref('')
const matchLoading = ref(false)
const matchResult = ref<MatchVO | null>(null)

const timers = new Map<string, number>()
const notified = new Set<string>()

const ALLOWED_EXTENSIONS = ['txt', 'md', 'docx', 'pdf']
const MAX_SIZE = 10 * 1024 * 1024
const POLL_INTERVAL = 1200
const MAX_ATTEMPTS = 30

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = await fetchMyResumes()
    list.value
      .filter((item) => item.status === 'PARSING' || item.status === 'UPLOADED')
      .forEach((item) => startPolling(item.id))
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '加载简历失败')
  } finally {
    loading.value = false
  }
}

function stopPolling(id: string): void {
  const timer = timers.get(id)
  if (timer) {
    window.clearInterval(timer)
    timers.delete(id)
  }
}

async function refreshResume(id: string): Promise<void> {
  try {
    const detail = await fetchResume(id)
    const index = list.value.findIndex((item) => item.id === id)
    if (index >= 0) {
      list.value[index] = detail.resume
    }
  } catch {
    // 静默失败：下次进入列表会重新拉取
  }
}

function startPolling(id: string): void {
  stopPolling(id)
  let attempts = 0
  const timer = window.setInterval(async () => {
    attempts += 1
    try {
      const detail = await fetchResume(id)
      const index = list.value.findIndex((item) => item.id === id)
      if (index >= 0) {
        list.value[index] = detail.resume
      }
      if (detail.resume.status === 'PARSED' && !notified.has(id)) {
        notified.add(id)
        ElMessage.success('简历解析完成')
      }
      if (detail.resume.status === 'FAILED' && !notified.has(id)) {
        notified.add(id)
        ElMessage.error(detail.resume.errorMessage || '简历解析失败')
      }
      if (detail.resume.status === 'PARSED' || detail.resume.status === 'FAILED' || attempts >= MAX_ATTEMPTS) {
        stopPolling(id)
        // 状态与解析结果可能不在同一次读取中到位，补一次拉取，避免出现「已解析但没有技能」的中间态
        if (detail.resume.status === 'PARSED') {
          window.setTimeout(() => void refreshResume(id), 600)
        }
      }
    } catch {
      if (attempts >= MAX_ATTEMPTS) stopPolling(id)
    }
  }, POLL_INTERVAL)
  timers.set(id, timer)
}

function validateFile(file: File): boolean {  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    ElMessage.warning('仅支持 txt / md / docx / pdf 格式')
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.warning('文件不能超过 10MB')
    return false
  }
  return true
}

async function onFileChange(uploadFile: UploadFile): Promise<void> {
  const raw = uploadFile.raw
  if (!raw) return
  if (!validateFile(raw)) return
  uploading.value = true
  uploadProgress.value = 0
  try {
    const created = await uploadResume(raw, (percent) => {
      uploadProgress.value = percent
    })
    list.value.unshift(created)
    startPolling(created.id)
    ElMessage.success('上传成功，正在解析…')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '上传失败')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

function openPaste(): void {
  pasteContent.value = ''
  pasteVisible.value = true
}

async function submitPaste(): Promise<void> {
  const content = pasteContent.value.trim()
  if (!content) {
    ElMessage.warning('请先粘贴简历内容')
    return
  }
  pasteSubmitting.value = true
  try {
    const created = await createResumeFromText(content)
    list.value.unshift(created)
    startPolling(created.id)
    pasteVisible.value = false
    ElMessage.success('已创建，正在解析…')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '创建失败')
  } finally {
    pasteSubmitting.value = false
  }
}

async function remove(resume: ResumeVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除简历「${resume.fileName || '未命名简历'}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'error',
    })
  } catch {
    return
  }
  try {
    await deleteResume(resume.id)
    stopPolling(resume.id)
    list.value = list.value.filter((item) => item.id !== resume.id)
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '删除失败')
  }
}

async function openMatch(resume: ResumeVO): Promise<void> {
  matchTarget.value = resume
  matchVisible.value = true
  matchResult.value = null
  matchJobId.value = ''
  if (matchJobs.value.length === 0) {
    matchJobsLoading.value = true
    try {
      const result = await fetchJobs({ status: 'OPEN', page: 1, size: 100 })
      matchJobs.value = result.records
    } catch (error) {
      ElMessage.error(error instanceof ApiError ? error.message : '加载岗位失败')
    } finally {
      matchJobsLoading.value = false
    }
  }
}

async function runMatch(): Promise<void> {
  if (!matchTarget.value || !matchJobId.value) return
  matchLoading.value = true
  try {
    matchResult.value = await matchResume(matchTarget.value.id, matchJobId.value)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '匹配失败')
  } finally {
    matchLoading.value = false
  }
}

function resetMatch(): void {
  matchTarget.value = null
  matchResult.value = null
  matchJobId.value = ''
}

onMounted(load)

onUnmounted(() => {
  timers.forEach((timer) => window.clearInterval(timer))
  timers.clear()
})
</script>

<style scoped lang="scss">
.upload-card {
  padding: 20px;

  :deep(.el-upload-dragger) {
    padding: 28px 20px;
    border-radius: var(--ai-radius-md);
  }
}

.resume-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
  margin-top: 16px;
  min-height: 60px;
}

.resume-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  &__title {
    display: flex;
    gap: 10px;
    min-width: 0;
  }

  &__icon {
    color: var(--ai-primary);
    font-size: 20px;
    margin-top: 2px;
    flex-shrink: 0;
  }

  &__name-wrap {
    min-width: 0;
  }

  &__name {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    font-size: 12px;
    margin-top: 4px;
  }

  &__skills {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-height: 24px;
  }

  &__more {
    font-size: 12px;
    line-height: 24px;
  }

  &__actions {
    display: flex;
    gap: 4px;
    margin-top: auto;
    padding-top: 12px;
    border-top: 1px dashed var(--ai-border);
  }
}

.match-result {
  display: flex;
  gap: 20px;
  margin-top: 20px;
  padding: 16px;
  border-radius: var(--ai-radius-md);
  background: var(--ai-bg);

  &__score {
    display: flex;
    align-items: baseline;
    justify-content: center;
    min-width: 96px;
    height: 96px;
    border-radius: 50%;
    background: #fff;
    box-shadow: var(--ai-shadow-card);
    font-size: 30px;
    font-weight: 700;
    line-height: 96px;

    small {
      font-size: 12px;
      font-weight: 400;
      margin-left: 2px;
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

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}

@media (max-width: 768px) {
  .resume-grid {
    grid-template-columns: 1fr;
  }
}
</style>
