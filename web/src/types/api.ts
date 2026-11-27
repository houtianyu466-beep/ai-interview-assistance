import type {
  JobLevel,
  JobStatus,
  InterviewMode,
  InterviewStatus,
  Recommendation,
  ResumeStatus,
  UserRole,
  UserStatus,
} from '@/utils/constants'

/** 后端统一响应体 */
export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页响应 */
export interface PageResult<T> {
  total: number
  current: number
  size: number
  pages: number
  records: T[]
}

export interface PageQuery {
  page?: number
  size?: number
}

export interface UserVO {
  id: string
  username: string
  realName?: string
  email?: string
  phone?: string
  role: UserRole
  status: UserStatus
  avatar?: string
  lastLoginAt?: string
  createdAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  realName?: string
  email?: string
  phone?: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: UserVO
}

export interface UpdateProfileRequest {
  realName?: string
  email?: string
  phone?: string
  avatar?: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface JobVO {
  id: string
  title: string
  department?: string
  level?: JobLevel
  description?: string
  requirements?: string
  status: JobStatus
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface JobSaveRequest {
  title: string
  department?: string
  level?: JobLevel
  description?: string
  requirements?: string
  status?: JobStatus
}

export interface JobQuery extends PageQuery {
  keyword?: string
  status?: JobStatus | ''
}

export interface ResumeVO {
  id: string
  fileName?: string
  yearsOfExperience?: number
  education?: string
  skills?: string
  status: ResumeStatus
  errorMessage?: string
  createdAt?: string
}

export interface WorkExperience {
  company?: string
  title?: string
  period?: string
  description?: string
}

export interface ProjectExperience {
  name?: string
  role?: string
  period?: string
  description?: string
}

export interface ParsedResume {
  name?: string
  email?: string
  phone?: string
  yearsOfExperience?: number
  education?: string
  skills: string[]
  workExperiences: WorkExperience[]
  projects: ProjectExperience[]
  highlights: string[]
  summary?: string
}

export interface ResumeDetailVO {
  resume: ResumeVO
  rawText?: string
  parsed?: ParsedResume | null
}

export interface MatchVO {
  score: number
  matchedSkills: string[]
  missingSkills: string[]
  advice?: string
}

export interface QuestionVO {
  id: string
  positionId?: string
  category: string
  difficulty: number
  content: string
  referenceAnswer?: string
  keywords?: string
  tags?: string
  source?: string
  createdAt?: string
}

export interface InterviewVO {
  id: string
  positionId: string
  positionTitle?: string
  resumeId?: string
  mode: InterviewMode
  status: InterviewStatus
  totalQuestions: number
  currentIndex: number
  durationSeconds?: number
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface CurrentQuestionVO {
  questionId: string
  seq: number
  totalQuestions: number
  content: string
  category?: string
  difficulty?: number
  referenceAnswer?: string
}

export interface AnswerResultVO {
  questionId: string
  score: number
  dimensionScores: Record<string, number>
  comment?: string
  hits: string[]
  misses: string[]
  finished: boolean
  nextQuestion?: CurrentQuestionVO
  reportId?: string
}

export interface QuestionReviewVO {
  seq: number
  content: string
  answer?: string
  score?: number
  dimensionScores: Record<string, number>
  comment?: string
}

export interface ReportVO {
  id: string
  interviewId: string
  overallScore: number
  dimensionScores: Record<string, number>
  strengths: string[]
  weaknesses: string[]
  suggestion: Recommendation
  summary?: string
  createdAt?: string
  questions: QuestionReviewVO[]
}

export interface StatsOverviewVO {
  userCount: number
  candidateCount: number
  interviewerCount: number
  interviewCount: number
  finishedCount: number
  inProgressCount: number
  averageScore: number
  last7Days: { date: string; count: number }[]
  statusDistribution: { status: string; count: number }[]
  hotPositions: { positionId: string; title: string; interviewCount: number }[]
}
