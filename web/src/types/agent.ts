/** 知识库引用片段 */
export interface Citation {
  title: string
  snippet: string
  score: number
}

/** 后端 Agent 工具调用记录 */
export interface ToolCall {
  name: string
  arguments: string
  resultSummary: string
}

/** AI 教练回答 */
export interface CoachResponse {
  answer: string
  citations: Citation[]
  toolCalls: ToolCall[]
  fallback: boolean
  latencyMs: number
}

/** Agent 调用链追踪（调试用） */
export interface AgentTrace {
  id: string
  sessionId?: string
  scene: string
  input: string
  output?: string
  toolCalls?: string
  success?: boolean
  latencyMs?: number
  createdAt?: string
}
