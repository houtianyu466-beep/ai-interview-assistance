import { get, post } from '@/api/http'
import type { AnswerResultVO, CurrentQuestionVO, PageResult, ReportVO } from '@/types/api'
import type {
  CreatePracticeRequest,
  PracticeAnswerRequest,
  PracticeReviewVO,
  PracticeSessionVO,
  PracticeSkipResult,
  PracticeTargetVO,
} from '@/types/practice'

const BASE = '/v1/practice'

/** 后端出题（AI 生成）可能需要 10-30 秒，单独放宽超时 */
const GENERATE_TIMEOUT = 120_000

export function fetchPracticeTargets(): Promise<PracticeTargetVO[]> {
  return get<PracticeTargetVO[]>(`${BASE}/targets`)
}

export function fetchPracticeTarget(id: string): Promise<PracticeTargetVO> {
  return get<PracticeTargetVO>(`${BASE}/targets/${id}`)
}

export function createPracticeSession(body: CreatePracticeRequest): Promise<PracticeSessionVO> {
  return post<PracticeSessionVO>(`${BASE}/sessions`, body, { timeout: GENERATE_TIMEOUT })
}

export function fetchMyPracticeSessions(params: {
  page?: number
  size?: number
  status?: string
}): Promise<PageResult<PracticeSessionVO>> {
  return get<PageResult<PracticeSessionVO>>(`${BASE}/sessions/my`, { ...params })
}

export function fetchPracticeSession(id: string): Promise<PracticeSessionVO> {
  return get<PracticeSessionVO>(`${BASE}/sessions/${id}`)
}

export function startPracticeSession(id: string): Promise<CurrentQuestionVO> {
  return post<CurrentQuestionVO>(`${BASE}/sessions/${id}/start`)
}

export function fetchCurrentQuestion(id: string): Promise<CurrentQuestionVO> {
  return get<CurrentQuestionVO>(`${BASE}/sessions/${id}/current`)
}

export function submitPracticeAnswer(
  id: string,
  body: PracticeAnswerRequest,
): Promise<AnswerResultVO> {
  return post<AnswerResultVO>(`${BASE}/sessions/${id}/answers`, body)
}

export function fetchPracticeHint(id: string): Promise<{ hint: string }> {
  return post<{ hint: string }>(`${BASE}/sessions/${id}/hint`)
}

export function skipPracticeQuestion(id: string): Promise<PracticeSkipResult> {
  return post<PracticeSkipResult>(`${BASE}/sessions/${id}/skip`)
}

export function finishPracticeSession(id: string): Promise<ReportVO> {
  return post<ReportVO>(`${BASE}/sessions/${id}/finish`)
}

export function fetchPracticeReview(id: string): Promise<PracticeReviewVO> {
  return get<PracticeReviewVO>(`${BASE}/sessions/${id}/review`)
}

export function cancelPracticeSession(id: string): Promise<void> {
  return post<void>(`${BASE}/sessions/${id}/cancel`)
}
