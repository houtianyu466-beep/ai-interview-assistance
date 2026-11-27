import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { LoginRequest, RegisterRequest, UserVO } from '@/types/api'
import type { UserRole } from '@/utils/constants'
import { clearToken, getToken, setToken } from '@/utils/storage'

/**
 * 登录态。
 *
 * - 只持久化 token；用户信息每次刷新通过 /auth/me 拉取，避免角色变更后前端显示旧权限；
 * - reset() 只清本地状态，logout() 会先调用后端把令牌加入黑名单。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const user = ref<UserVO | null>(null)
  const loadingProfile = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed<UserRole | ''>(() => user.value?.role ?? '')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isInterviewer = computed(() => role.value === 'INTERVIEWER')
  const isCandidate = computed(() => role.value === 'CANDIDATE')
  const displayName = computed(() => user.value?.realName || user.value?.username || '未登录')

  function applyLogin(accessToken: string, loginUser: UserVO): void {
    token.value = accessToken
    user.value = loginUser
    setToken(accessToken)
  }

  async function login(payload: LoginRequest): Promise<void> {
    const result = await authApi.login(payload)
    applyLogin(result.accessToken, result.user)
  }

  async function register(payload: RegisterRequest): Promise<void> {
    const result = await authApi.register(payload)
    applyLogin(result.accessToken, result.user)
  }

  async function fetchProfile(): Promise<UserVO> {
    loadingProfile.value = true
    try {
      const profile = await authApi.fetchMe()
      user.value = profile
      return profile
    } finally {
      loadingProfile.value = false
    }
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch {
      // 令牌可能已过期，登出失败也要清理本地状态
    } finally {
      reset()
    }
  }

  function reset(): void {
    token.value = ''
    user.value = null
    clearToken()
  }

  return {
    token,
    user,
    loadingProfile,
    isLoggedIn,
    role,
    isAdmin,
    isInterviewer,
    isCandidate,
    displayName,
    login,
    register,
    fetchProfile,
    logout,
    reset,
  }
})
