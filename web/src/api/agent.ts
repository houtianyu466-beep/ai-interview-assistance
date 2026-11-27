import { get, post } from '@/api/http'
import type { AgentTrace, CoachResponse } from '@/types/agent'

const BASE = '/v1/practice'

/** 教练可能经历「检索知识库 → 生成回答」多步，单独放宽超时 */
const COACH_TIMEOUT = 90_000

/** 向练习房间的 AI 教练提问 */
export function askCoach(sessionId: string, message: string): Promise<CoachResponse> {
  return post<CoachResponse>(
    `${BASE}/sessions/${sessionId}/coach`,
    { message },
    { timeout: COACH_TIMEOUT },
  )
}

/** 查询会话的 Agent 调用链（调试用，可不在页面展示） */
export function fetchCoachTraces(sessionId: string): Promise<AgentTrace[]> {
  return get<AgentTrace[]>(`${BASE}/sessions/${sessionId}/coach/traces`)
}
