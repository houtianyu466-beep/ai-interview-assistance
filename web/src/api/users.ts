import { put } from '@/api/http'
import type { ChangePasswordRequest, UpdateProfileRequest, UserVO } from '@/types/api'

export function updateProfile(payload: UpdateProfileRequest): Promise<UserVO> {
  return put<UserVO>('/v1/users/me', payload)
}

export function changePassword(payload: ChangePasswordRequest): Promise<void> {
  return put<void>('/v1/users/me/password', payload)
}
