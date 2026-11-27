import 'vue-router'
import type { UserRole } from '@/utils/constants'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，用于浏览器标题与面包屑 */
    title?: string
    /** 是否无需登录即可访问 */
    public?: boolean
    /** 允许访问的角色，不设置表示所有已登录用户 */
    roles?: UserRole[]
  }
}
