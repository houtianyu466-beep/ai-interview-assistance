import { get, post } from '@/api/http'
import type { LoginRequest, LoginResponse, RegisterRequest, UserVO } from '@/types/api'

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/v1/auth/login', payload)
}

export function register(payload: RegisterRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/v1/auth/register', payload)
}

export function fetchMe(): Promise<UserVO> {
  return get<UserVO>('/v1/auth/me')
}

export function logout(): Promise<void> {
  return post<void>('/v1/auth/logout')
}
