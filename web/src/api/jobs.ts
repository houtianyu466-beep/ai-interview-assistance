import { del, get, patch, post, put } from '@/api/http'
import type { JobQuery, JobSaveRequest, JobVO, PageResult } from '@/types/api'
import type { JobStatus } from '@/utils/constants'

export function fetchJobs(query: JobQuery): Promise<PageResult<JobVO>> {
  return get<PageResult<JobVO>>('/v1/jobs', { ...query })
}

export function fetchJob(id: string): Promise<JobVO> {
  return get<JobVO>(`/v1/jobs/${id}`)
}

export function createJob(payload: JobSaveRequest): Promise<JobVO> {
  return post<JobVO>('/v1/jobs', payload)
}

export function updateJob(id: string, payload: JobSaveRequest): Promise<JobVO> {
  return put<JobVO>(`/v1/jobs/${id}`, payload)
}

export function changeJobStatus(id: string, status: JobStatus): Promise<JobVO> {
  return patch<JobVO>(`/v1/jobs/${id}/status`, undefined, { params: { status } })
}

export function deleteJob(id: string): Promise<void> {
  return del<void>(`/v1/jobs/${id}`)
}
