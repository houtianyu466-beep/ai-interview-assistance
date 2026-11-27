import { del, get, post, postForm } from '@/api/http'
import type { MatchVO, ResumeDetailVO, ResumeVO } from '@/types/api'

export function uploadResume(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<ResumeVO> {
  const form = new FormData()
  form.append('file', file)
  return postForm<ResumeVO>('/v1/resumes', form, onProgress)
}

export function createResumeFromText(content: string): Promise<ResumeVO> {
  return post<ResumeVO>('/v1/resumes/text', { content })
}

export function fetchMyResumes(): Promise<ResumeVO[]> {
  return get<ResumeVO[]>('/v1/resumes/my')
}

export function fetchResume(id: string): Promise<ResumeDetailVO> {
  return get<ResumeDetailVO>(`/v1/resumes/${id}`)
}

export function deleteResume(id: string): Promise<void> {
  return del<void>(`/v1/resumes/${id}`)
}

export function matchResume(id: string, positionId: string): Promise<MatchVO> {
  return get<MatchVO>(`/v1/resumes/${id}/match`, { positionId })
}
