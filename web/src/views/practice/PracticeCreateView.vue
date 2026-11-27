<template>
  <div class="create">
    <PageHeader title="开始练习" subtitle="选方向、选简历、定题量，AI 会结合你的经历现场出题" back />

    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <section class="card step">
          <header class="step__head">
            <span class="step__index">1</span>
            <div>
              <h3 class="step__title">选择面试方向</h3>
              <p class="step__hint">决定 AI 的出题范围，之后也可以随时更换</p>
            </div>
          </header>

          <div v-if="targetsLoading" class="target-options">
            <div v-for="n in 4" :key="n" class="target-option target-option--skeleton">
              <el-skeleton :rows="2" animated />
            </div>
          </div>

          <el-alert
            v-else-if="targetsError"
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
            description="稍后再试，或先去面试指南做好准备"
            :icon="Compass"
          >
            <template #action>
              <el-button type="primary" @click="loadTargets">重新加载</el-button>
            </template>
          </EmptyState>

          <div v-else class="target-options">
            <button
              v-for="target in targets"
              :key="target.id"
              type="button"
              class="target-option"
              :class="{ 'is-active': form.targetId === target.id }"
              @click="selectTarget(target)"
            >
              <span class="target-option__icon">
                <el-icon :size="20"><component :is="resolveIcon(target.icon)" /></el-icon>
              </span>
              <span class="target-option__main">
                <strong>{{ target.title }}</strong>
                <small>{{ target.subtitle || practiceCategoryLabel(target.category) }}</small>
              </span>
              <el-icon v-if="form.targetId === target.id" class="target-option__check">
                <CircleCheckFilled />
              </el-icon>
            </button>
          </div>
        </section>

        <section class="card step">
          <header class="step__head">
            <span class="step__index">2</span>
            <div>
              <h3 class="step__title">选择简历</h3>
              <p class="step__hint">可选。带上简历，AI 会围绕你的项目经历追问</p>
            </div>
          </header>

          <div v-if="resumesLoading" class="resume-options">
            <el-skeleton :rows="3" animated />
          </div>

          <template v-else>
            <el-radio-group v-model="form.resumeId" class="resume-options">
              <el-radio value="" class="resume-option">
                <span class="resume-option__main">
                  <strong>不使用简历（通用练习）</strong>
                  <small>使用方向的通用题库，适合第一次练习</small>
                </span>
              </el-radio>
              <el-radio
                v-for="resume in parsedResumes"
                :key="resume.id"
                :value="resume.id"
                class="resume-option"
              >
                <span class="resume-option__main">
                  <strong>{{ resume.fileName || '未命名简历' }}</strong>
                  <small>{{ resumeMeta(resume) }}</small>
                </span>
              </el-radio>
            </el-radio-group>

            <el-alert
              v-if="!resumesError && parsedResumes.length === 0"
              type="info"
              :closable="false"
              show-icon
              class="mt-3"
              title="还没有解析完成的简历"
            >
              <template #default>
                可以先
                <router-link :to="{ name: 'resumes' }" class="resume-options__link">去上传简历</router-link>
                ，或者在本次练习中使用通用题目。
              </template>
            </el-alert>

            <p v-if="resumesError" class="step__error">{{ resumesError }}</p>
          </template>
        </section>

        <section class="card step">
          <header class="step__head">
            <span class="step__index">3</span>
            <div>
              <h3 class="step__title">面试模式</h3>
              <p class="step__hint">不同模式的问题侧重点不同</p>
            </div>
          </header>
          <el-radio-group v-model="form.mode">
            <el-radio-button v-for="mode in MODE_OPTIONS" :key="mode.value" :value="mode.value">
              {{ mode.label }}
            </el-radio-button>
          </el-radio-group>
          <p class="step__hint step__hint--mode">{{ modeHint }}</p>
        </section>

        <section class="card step">
          <header class="step__head">
            <span class="step__index">4</span>
            <div>
              <h3 class="step__title">题目数量</h3>
              <p class="step__hint">题量越多越完整，第一次建议 5 题左右</p>
            </div>
          </header>
          <div class="question-count">
            <el-input-number v-model="form.totalQuestions" :min="3" :max="15" :step="1" />
            <span class="step__hint">预计 {{ estimateMinutes }} 分钟</span>
          </div>
        </section>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="create__tip"
          title="支持文字作答；也可以在练习中直接录音，系统会自动转写成文字。"
        />

        <div class="create__actions">
          <el-button @click="goHome">取消</el-button>
          <el-button type="primary" size="large" :loading="submitting" @click="submit">
            {{ submitting ? 'AI 正在出题…' : '开始练习' }}
          </el-button>
        </div>
        <p class="create__note">AI 出题约需 10-30 秒，请耐心等待，不要重复点击。</p>
      </el-col>

      <el-col :xs="24" :lg="8">
        <aside class="card preview">
          <template v-if="selectedTarget">
            <p class="preview__eyebrow">已选方向</p>
            <div class="preview__head">
              <span class="preview__icon">
                <el-icon :size="22"><component :is="resolveIcon(selectedTarget.icon)" /></el-icon>
              </span>
              <div>
                <h3 class="preview__title">{{ selectedTarget.title }}</h3>
                <p class="preview__category">{{ practiceCategoryLabel(selectedTarget.category) }}</p>
              </div>
            </div>
            <p class="preview__desc">
              {{ selectedTarget.description || selectedTarget.subtitle || '围绕这个方向，来一场针对性的模拟面试。' }}
            </p>

            <h4 class="preview__label">练习重点</h4>
            <div class="preview__topics">
              <el-tag
                v-for="topic in selectedTarget.focusTopics"
                :key="topic"
                size="small"
                effect="plain"
                type="info"
              >
                {{ topic }}
              </el-tag>
              <span v-if="selectedTarget.focusTopics.length === 0" class="muted">暂无标签</span>
            </div>

            <el-divider />

            <dl class="preview__meta">
              <div>
                <dt>默认题数</dt>
                <dd>{{ selectedTarget.defaultQuestions }} 题</dd>
              </div>
              <div>
                <dt>难度</dt>
                <dd><el-rate :model-value="selectedTarget.difficulty" disabled :max="5" size="small" /></dd>
              </div>
            </dl>
          </template>

          <div v-else class="preview__placeholder">
            <el-icon :size="34"><Compass /></el-icon>
            <p>选择左侧的方向后，这里会显示它的练习重点</p>
            <router-link :to="{ name: 'guide' }" class="preview__link">第一次面试？先看指南</router-link>
          </div>
        </aside>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  CircleCheck,
  CircleCheckFilled,
  Compass,
  Cpu,
  DataAnalysis,
  Goods,
  Monitor,
  Platform,
  Reading,
  Setting,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { practiceCategoryLabel } from '@/utils/constants'
import EmptyState from '@/components/EmptyState.vue'
import { fetchPracticeTargets, createPracticeSession } from '@/api/practice'
import { fetchMyResumes } from '@/api/resumes'
import { ApiError } from '@/api/http'
import type { CreatePracticeRequest, PracticeTargetVO } from '@/types/practice'
import type { ResumeVO } from '@/types/api'

interface ModeOption {
  value: CreatePracticeRequest['mode']
  label: string
  hint: string
}

const MODE_OPTIONS: ModeOption[] = [
  { value: 'TECH', label: '技术面', hint: '围绕方向的基础知识、项目细节与技术取舍提问' },
  { value: 'HR', label: 'HR 面', hint: '关注求职动机、稳定性、沟通表达与职业规划' },
  { value: 'COMPREHENSIVE', label: '综合面', hint: '技术与 HR 结合，最接近真实面试的节奏' },
]

const route = useRoute()
const router = useRouter()

const targets = ref<PracticeTargetVO[]>([])
const targetsLoading = ref(true)
const targetsError = ref('')

const resumes = ref<ResumeVO[]>([])
const resumesLoading = ref(true)
const resumesError = ref('')

const submitting = ref(false)

const form = reactive({
  targetId: '',
  resumeId: '',
  mode: 'COMPREHENSIVE' as CreatePracticeRequest['mode'],
  totalQuestions: 5,
})

const parsedResumes = computed(() => resumes.value.filter((item) => item.status === 'PARSED'))

const selectedTarget = computed(
  () => targets.value.find((item) => item.id === form.targetId) ?? null,
)

const modeHint = computed(() => MODE_OPTIONS.find((item) => item.value === form.mode)?.hint ?? '')

const estimateMinutes = computed(() => {
  const count = Number.isFinite(form.totalQuestions) ? form.totalQuestions : 5
  return Math.max(5, Math.round(count * 2.5))
})

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

function resumeMeta(resume: ResumeVO): string {
  const education = resume.education || '学历未知'
  const years = resume.yearsOfExperience != null ? `${resume.yearsOfExperience} 年经验` : '年限未知'
  return `${education} · ${years}`
}

function clampQuestions(value: number): number {
  const rounded = Number.isFinite(value) ? Math.round(value) : 5
  return Math.min(15, Math.max(3, rounded))
}

function selectTarget(target: PracticeTargetVO): void {
  form.targetId = target.id
  form.totalQuestions = clampQuestions(target.defaultQuestions || 5)
}

async function loadTargets(): Promise<void> {
  targetsLoading.value = true
  targetsError.value = ''
  try {
    targets.value = await fetchPracticeTargets()
    applyQueryTarget()
  } catch (error) {
    targetsError.value = error instanceof ApiError ? error.message : '加载练习方向失败，请稍后重试'
  } finally {
    targetsLoading.value = false
  }
}

function applyQueryTarget(): void {
  if (form.targetId) return
  const preset = typeof route.query.targetId === 'string' ? route.query.targetId : ''
  if (!preset) return
  const target = targets.value.find((item) => item.id === preset)
  if (target) selectTarget(target)
}

async function loadResumes(): Promise<void> {
  resumesLoading.value = true
  resumesError.value = ''
  try {
    resumes.value = await fetchMyResumes()
  } catch (error) {
    resumesError.value =
      error instanceof ApiError ? error.message : '简历加载失败，可直接使用通用题目练习'
  } finally {
    resumesLoading.value = false
  }
}

function goHome(): void {
  router.push({ name: 'practice-home' })
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!form.targetId) {
    ElMessage.warning('请先选择一个练习方向')
    return
  }

  submitting.value = true
  try {
    const session = await createPracticeSession({
      targetId: form.targetId,
      resumeId: form.resumeId || undefined,
      mode: form.mode,
      totalQuestions: clampQuestions(form.totalQuestions),
    })
    ElMessage.success('练习已创建，正在进入练习房间')
    await router.push({ name: 'practice-room', params: { id: session.id } })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '创建练习失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadTargets()
  loadResumes()
})
</script>

<style scoped lang="scss">
.step {
  margin-bottom: var(--ai-space-4);
  padding: 22px 24px 24px;

  &__head {
    display: flex;
    align-items: flex-start;
    gap: var(--ai-space-3);
    margin-bottom: var(--ai-space-4);
  }

  &__index {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    line-height: 26px;
  }

  &__hint {
    margin-top: 2px;
    font-size: 13px;
    color: var(--ai-text-secondary);
  }

  &__hint--mode {
    margin-top: var(--ai-space-3);
  }

  &__error {
    margin-top: var(--ai-space-2);
    font-size: 12px;
    color: var(--ai-danger);
  }
}

.target-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ai-space-3);
}

.target-option {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--ai-space-3);
  width: 100%;
  padding: 14px 16px;
  text-align: left;
  font: inherit;
  color: inherit;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: var(--ai-radius-md);
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: var(--ai-primary);
  }

  &.is-active {
    border-color: var(--ai-primary);
    background: var(--ai-primary-soft);
    box-shadow: 0 0 0 1px var(--ai-primary) inset;
  }

  &--skeleton {
    cursor: default;
    pointer-events: none;
  }

  &__icon {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &.is-active &__icon {
    background: #fff;
  }

  &__main {
    display: flex;
    flex-direction: column;
    min-width: 0;
    flex: 1;
  }

  &__main strong {
    font-size: 14px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__main small {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ai-text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__check {
    color: var(--ai-primary);
    font-size: 18px;
    flex-shrink: 0;
  }
}

.resume-options {
  display: flex;
  flex-direction: column;
  gap: var(--ai-space-2);

  &__link {
    margin: 0 2px;
  }
}

.resume-option {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--ai-border);
  border-radius: var(--ai-radius-md);
  white-space: normal;

  &__main {
    display: flex;
    flex-direction: column;
    line-height: 1.5;
  }

  &__main strong {
    font-size: 14px;
    font-weight: 500;
  }

  &__main small {
    font-size: 12px;
    color: var(--ai-text-secondary);
  }
}

.resume-option.is-checked {
  border-color: var(--ai-primary);
  background: var(--ai-primary-soft);
}

.question-count {
  display: flex;
  align-items: center;
  gap: var(--ai-space-3);
  flex-wrap: wrap;
}

.create__tip {
  margin-bottom: var(--ai-space-4);
}

.create__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--ai-space-3);
}

.create__note {
  margin-top: var(--ai-space-2);
  font-size: 12px;
  text-align: right;
  color: var(--ai-text-placeholder);
}

.preview {
  position: sticky;
  top: 0;

  &__eyebrow {
    font-size: 12px;
    color: var(--ai-text-placeholder);
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--ai-space-3);
    margin-top: var(--ai-space-2);
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
    flex-shrink: 0;
  }

  &__title {
    font-size: 17px;
    font-weight: 600;
  }

  &__category {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ai-text-placeholder);
  }

  &__desc {
    margin-top: var(--ai-space-3);
    font-size: 13px;
    line-height: 1.7;
    color: var(--ai-text-secondary);
  }

  &__label {
    margin-top: var(--ai-space-4);
    font-size: 13px;
    font-weight: 600;
  }

  &__topics {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: var(--ai-space-2);
  }

  &__meta {
    display: flex;
    gap: var(--ai-space-6);
    margin: 0;

    dt {
      font-size: 12px;
      color: var(--ai-text-secondary);
    }

    dd {
      margin: 6px 0 0;
      font-size: 14px;
      font-weight: 500;
    }
  }

  &__placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--ai-space-3);
    min-height: 260px;
    padding: var(--ai-space-4);
    text-align: center;
    color: var(--ai-text-placeholder);
  }

  &__placeholder p {
    font-size: 13px;
    line-height: 1.6;
  }

  &__link {
    font-size: 13px;
  }
}

@media (max-width: 768px) {
  .step {
    padding: 18px 16px;
  }

  .target-options {
    grid-template-columns: 1fr;
  }

  .preview {
    position: static;
    margin-top: var(--ai-space-4);
  }

  .create__actions {
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .create__actions :deep(.el-button) {
    margin-left: 0;
    width: 100%;
  }

  .create__note {
    text-align: center;
  }
}
</style>
