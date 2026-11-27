export type UserRole = 'CANDIDATE' | 'INTERVIEWER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED'
export type JobLevel = 'JUNIOR' | 'MIDDLE' | 'SENIOR'
export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED'
export type ResumeStatus = 'UPLOADED' | 'PARSING' | 'PARSED' | 'FAILED'
export type InterviewMode = 'TECH' | 'HR' | 'COMPREHENSIVE'
export type InterviewStatus = 'CREATED' | 'IN_PROGRESS' | 'FINISHED' | 'CANCELLED'
export type Recommendation = 'PASS' | 'HOLD' | 'REJECT'

export interface Option<T extends string = string> {
  value: T
  label: string
}

export interface TagMeta {
  label: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

export const ROLE_META: Record<UserRole, TagMeta> = {
  CANDIDATE: { label: '练习者', type: 'primary' },
  INTERVIEWER: { label: '面试官', type: 'success' },
  ADMIN: { label: '管理员', type: 'warning' },
}

export const JOB_STATUS_META: Record<JobStatus, TagMeta> = {
  DRAFT: { label: '草稿', type: 'info' },
  OPEN: { label: '招聘中', type: 'success' },
  CLOSED: { label: '已关闭', type: 'danger' },
}

export const JOB_LEVEL_META: Record<JobLevel, string> = {
  JUNIOR: '初级',
  MIDDLE: '中级',
  SENIOR: '高级',
}

export const RESUME_STATUS_META: Record<ResumeStatus, TagMeta> = {
  UPLOADED: { label: '待解析', type: 'info' },
  PARSING: { label: '解析中', type: 'primary' },
  PARSED: { label: '已解析', type: 'success' },
  FAILED: { label: '解析失败', type: 'danger' },
}

export const INTERVIEW_STATUS_META: Record<InterviewStatus, TagMeta> = {
  CREATED: { label: '待开始', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  FINISHED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'danger' },
}

export const INTERVIEW_MODE_META: Record<InterviewMode, string> = {
  TECH: '技术面',
  HR: 'HR 面',
  COMPREHENSIVE: '综合面',
}

export const RECOMMENDATION_META: Record<Recommendation, TagMeta> = {
  PASS: { label: '建议通过', type: 'success' },
  HOLD: { label: '待定', type: 'warning' },
  REJECT: { label: '不建议通过', type: 'danger' },
}

export const JOB_STATUS_OPTIONS: Option<JobStatus>[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'OPEN', label: '招聘中' },
  { value: 'CLOSED', label: '已关闭' },
]

export const JOB_LEVEL_OPTIONS: Option<JobLevel>[] = [
  { value: 'JUNIOR', label: '初级' },
  { value: 'MIDDLE', label: '中级' },
  { value: 'SENIOR', label: '高级' },
]

export const QUESTION_CATEGORY_OPTIONS = [
  'JAVA',
  'SPRING',
  'MYSQL',
  'REDIS',
  'DISTRIBUTED',
  'ALGORITHM',
  'BEHAVIOR',
]

export const DIFFICULTY_LABELS = ['', '入门', '简单', '中等', '较难', '困难']

/** 练习方向分类的中文展示（后端存的是英文 code） */
export const PRACTICE_CATEGORY_LABELS: Record<string, string> = {
  BACKEND: '后端',
  FRONTEND: '前端',
  PRODUCT: '产品',
  DATA: '数据',
  TEST: '测试',
  DEVOPS: '运维',
  ALGORITHM: '算法',
  GENERAL: '通用',
}

export function practiceCategoryLabel(category?: string | null): string {
  if (!category) return ''
  return PRACTICE_CATEGORY_LABELS[category.toUpperCase()] ?? category
}
