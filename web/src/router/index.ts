import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const AppShell = () => import('@/layouts/AppShell.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', public: true },
  },
  // 视频通话是独立全屏页：不套 AppShell，避免侧边栏挤压画面
  {
    path: '/call/:code',
    name: 'video-call',
    component: () => import('@/views/video/VideoCallView.vue'),
    meta: { title: '视频通话' },
  },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: '/practice' },
      // 兼容占位：PageHeader / 403 / 404 等公共组件仍以 name: 'dashboard' 回跳
      { path: 'dashboard', name: 'dashboard', redirect: '/practice' },
      {
        path: 'practice',
        name: 'practice-home',
        component: () => import('@/views/practice/PracticeHomeView.vue'),
        meta: { title: '练习工作台' },
      },
      {
        path: 'practice/new',
        name: 'practice-create',
        component: () => import('@/views/practice/PracticeCreateView.vue'),
        meta: { title: '开始练习' },
      },
      {
        path: 'practice/history',
        name: 'practice-history',
        component: () => import('@/views/practice/PracticeHistoryView.vue'),
        meta: { title: '练习记录' },
      },
      {
        path: 'practice/:id/room',
        name: 'practice-room',
        component: () => import('@/views/practice/PracticeRoomView.vue'),
        meta: { title: '练习中' },
      },
      {
        path: 'practice/:id/review',
        name: 'practice-review',
        component: () => import('@/views/practice/PracticeReviewView.vue'),
        meta: { title: '练习复盘' },
      },
      {
        path: 'video',
        name: 'video-lobby',
        component: () => import('@/views/video/VideoLobbyView.vue'),
        meta: { title: '视频面试' },
      },
      {
        path: 'resumes',
        name: 'resumes',
        component: () => import('@/views/resume/ResumeListView.vue'),
        meta: { title: '我的简历', roles: ['CANDIDATE', 'ADMIN'] },
      },
      {
        path: 'resumes/:id',
        name: 'resume-detail',
        component: () => import('@/views/resume/ResumeDetailView.vue'),
        meta: { title: '简历详情', roles: ['CANDIDATE', 'ADMIN'] },
      },
      {
        path: 'guide',
        name: 'guide',
        component: () => import('@/views/guide/GuideView.vue'),
        meta: { title: '面试指南' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/views/error/ForbiddenView.vue'),
        meta: { title: '无权限' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/error/NotFoundView.vue'),
        meta: { title: '页面不存在', public: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const appTitle = import.meta.env.VITE_APP_TITLE || 'AI 面试练习'
  document.title = to.meta.title ? `${to.meta.title} · ${appTitle}` : appTitle

  if (to.meta.public) {
    if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
      return { name: 'practice-home' }
    }
    return true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!auth.user) {
    try {
      await auth.fetchProfile()
    } catch {
      auth.reset()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  const roles = to.meta.roles
  if (roles && !roles.includes(auth.role as never)) {
    return { name: 'forbidden' }
  }
  return true
})

export default router
