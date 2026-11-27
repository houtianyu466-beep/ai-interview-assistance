import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { getToken } from '@/utils/storage'

/** 统一错误对象：页面只依赖 status / code / message */
export class ApiError extends Error {
  readonly status: number
  readonly code: number

  constructor(status: number, code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30_000,
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown>
    // 后端所有接口都返回 Result；若不是（如静态资源），原样返回
    if (body && typeof body === 'object' && typeof body.code === 'number') {
      if (body.code !== 0) {
        return Promise.reject(new ApiError(response.status, body.code, body.message))
      }
      return body.data
    }
    return response.data
  },
  (error: AxiosError<Result<unknown>>) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data
    const fallback = status === 0 ? '网络异常，请检查网络后重试' : error.message || '请求失败'
    if (status === 401) {
      // 由 main.ts 监听：清空登录态并跳转登录页，避免 http 层依赖 router
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    }
    return Promise.reject(new ApiError(status, body?.code ?? status, body?.message || fallback))
  },
)

export function get<T>(
  url: string,
  params?: Record<string, unknown>,
  config?: AxiosRequestConfig,
): Promise<T> {
  return instance.get(url, { params, ...config }) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>
}

export function patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.patch(url, data, config) as unknown as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>
}

/** multipart 上传，带进度回调 */
export function postForm<T>(
  url: string,
  form: FormData,
  onProgress?: (percent: number) => void,
): Promise<T> {
  return instance.post(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60_000,
    onUploadProgress: (event) => {
      if (event.total) {
        onProgress?.(Math.round((event.loaded / event.total) * 100))
      }
    },
  }) as unknown as Promise<T>
}
