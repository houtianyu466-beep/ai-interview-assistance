/** 后端返回的时间已是 `yyyy-MM-dd HH:mm:ss`，这里只做兼容与空值处理。 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function formatDate(value?: string | null): string {
  if (!value) return '—'
  return value.slice(0, 10)
}

export function formatDuration(seconds?: number | null): string {
  if (seconds == null || seconds < 0) return '—'
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes < 60) return rest ? `${minutes} 分 ${rest} 秒` : `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  return `${hours} 小时 ${minutes % 60} 分`
}

export function formatScore(score?: number | null): string {
  if (score == null) return '—'
  return Number.isInteger(score) ? String(score) : score.toFixed(1)
}

export type ScoreLevel = 'high' | 'middle' | 'low'

export function scoreLevel(score?: number | null): ScoreLevel {
  if (score == null) return 'low'
  if (score >= 80) return 'high'
  if (score >= 60) return 'middle'
  return 'low'
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 把逗号分隔的技能字符串转数组 */
export function splitSkills(skills?: string | null): string[] {
  if (!skills) return []
  return skills
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
