const TOKEN_KEY = 'ai-interview:token'

/**
 * token 读写。
 *
 * 单独放在 storage 模块，供 http 拦截器使用，避免「store → api → http → store」循环依赖。
 */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 面试草稿（下一阶段面试房间使用） */
export function saveDraft(interviewId: string, seq: number, content: string): void {
  localStorage.setItem(`ai-interview:draft:${interviewId}:${seq}`, content)
}

export function readDraft(interviewId: string, seq: number): string {
  return localStorage.getItem(`ai-interview:draft:${interviewId}:${seq}`) ?? ''
}

export function clearDraft(interviewId: string): void {
  Object.keys(localStorage)
    .filter((key) => key.startsWith(`ai-interview:draft:${interviewId}:`))
    .forEach((key) => localStorage.removeItem(key))
}
