/** 练习复盘等级 */
export type PracticeLevel = 'EXCELLENT' | 'GOOD' | 'IMPROVING' | 'NEEDS_WORK'

/** 练习方向 */
export interface PracticeTargetVO {
  id: string
  code: string
  title: string
  subtitle?: string
  category: string
  description?: string
  focusTopics: string[]
  defaultQuestions: number
  difficulty: number
  icon?: string
}

/** 创建练习请求 */
export interface CreatePracticeRequest {
  targetId: string
  resumeId?: string
  mode: 'TECH' | 'HR' | 'COMPREHENSIVE'
  totalQuestions?: number
}

/** 练习场次 */
export interface PracticeSessionVO {
  id: string
  targetId: string
  targetTitle: string
  targetSubtitle?: string
  mode: string
  status: 'CREATED' | 'IN_PROGRESS' | 'FINISHED' | 'CANCELLED'
  totalQuestions: number
  currentIndex: number
  answeredCount: number
  score?: number
  createdAt?: string
  finishedAt?: string
  durationSeconds?: number
}

/** 提交回答请求 */
export interface PracticeAnswerRequest {
  questionId: string
  answer: string
  thinkingSeconds?: number
  audioPath?: string
}

/** 单题复盘 */
export interface PracticeQuestionReviewVO {
  seq: number
  content: string
  answer?: string
  score?: number
  dimensionScores: Record<string, number>
  comment?: string
  referenceAnswer?: string
}

/** 整场练习复盘 */
export interface PracticeReviewVO {
  session: PracticeSessionVO
  overallScore: number
  dimensionScores: Record<string, number>
  level: PracticeLevel
  levelLabel: string
  strengths: string[]
  weaknesses: string[]
  summary?: string
  questions: PracticeQuestionReviewVO[]
  nextSteps: string[]
}

/** 跳过当前题的结果 */
export interface PracticeSkipResult {
  finished: boolean
  nextQuestion?: import('@/types/api').CurrentQuestionVO
  reportId?: string
}
