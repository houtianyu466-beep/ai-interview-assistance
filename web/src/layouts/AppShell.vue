<template>
  <el-container class="shell">
    <el-aside class="shell__aside" :width="ui.sidebarCollapsed ? '64px' : '220px'">
      <div class="shell__brand" @click="goHome">
        <div class="shell__logo">AI</div>
        <span v-show="!ui.sidebarCollapsed" class="shell__brand-text">AI 面试练习</span>
      </div>
      <el-menu :default-active="activeMenu" :collapse="ui.sidebarCollapsed" router class="shell__menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
      <div v-show="!ui.sidebarCollapsed" class="shell__aside-footer">面试练习 · 单用户版</div>
    </el-aside>

    <el-container class="shell__body">
      <el-header class="shell__header" height="60px">
        <div class="flex-center gap-2">
          <el-button text class="shell__mobile-menu" @click="ui.openMobileMenu">
            <el-icon><Menu /></el-icon>
          </el-button>
          <el-button text class="shell__collapse" @click="ui.toggleSidebar">
            <el-icon>
              <Expand v-if="ui.sidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ name: 'practice-home' }">练习工作台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title && route.name !== 'practice-home'">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="flex-center gap-3">
          <StatusTag kind="role" :status="auth.role" />
          <el-dropdown trigger="click" @command="onCommand">
            <span class="shell__user">
              <el-avatar :size="30" :src="auth.user?.avatar">{{ avatarText }}</el-avatar>
              <span class="shell__username">{{ auth.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="shell__main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <el-drawer
      v-model="ui.mobileMenuVisible"
      direction="ltr"
      size="220px"
      :with-header="false"
      class="shell__drawer"
    >
      <div class="shell__brand" @click="goHome">
        <div class="shell__logo">AI</div>
        <span class="shell__brand-text">AI 面试练习</span>
      </div>
      <el-menu :default-active="activeMenu" router @select="ui.closeMobileMenu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Clock,
  Document,
  Expand,
  Fold,
  Menu,
  Odometer,
  Reading,
  User,
  VideoCamera,
  VideoPlay,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import StatusTag from '@/components/StatusTag.vue'

interface MenuItem {
  path: string
  label: string
  icon: Component
}

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const menus: MenuItem[] = [
  { path: '/practice', label: '练习工作台', icon: Odometer },
  { path: '/practice/new', label: '开始练习', icon: VideoPlay },
  { path: '/practice/history', label: '练习记录', icon: Clock },
  { path: '/video', label: '视频面试', icon: VideoCamera },
  { path: '/guide', label: '面试指南', icon: Reading },
  { path: '/resumes', label: '我的简历', icon: Document },
  { path: '/profile', label: '个人中心', icon: User },
]

const activeMenu = computed(() => {
  const path = route.path
  if (path === '/practice/new') return '/practice/new'
  if (path === '/practice/history') return '/practice/history'
  // 练习房间 / 复盘（/practice/:id/room|review）高亮「练习记录」
  if (path.startsWith('/practice/')) return '/practice/history'
  if (path.startsWith('/practice')) return '/practice'
  // 视频通话是全屏独立页，高亮「视频面试」
  if (path.startsWith('/video') || path.startsWith('/call')) return '/video'
  if (path.startsWith('/resumes')) return '/resumes'
  if (path.startsWith('/guide')) return '/guide'
  if (path.startsWith('/profile')) return '/profile'
  return '/practice'
})

const avatarText = computed(() => auth.displayName.slice(0, 1).toUpperCase())

function goHome(): void {
  ui.closeMobileMenu()
  router.push({ name: 'practice-home' })
}

async function onCommand(command: string): Promise<void> {
  if (command === 'profile') {
    router.push({ name: 'profile' })
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
    await auth.logout()
    ElMessage.success('已退出登录')
    router.replace({ name: 'login' })
  }
}
</script>

<style scoped lang="scss">
.shell {
  height: 100vh;

  &__aside {
    display: flex;
    flex-direction: column;
    background: #121827;
    transition: width 0.2s ease;
    overflow: hidden;
  }

  &__brand {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 60px;
    padding: 0 18px;
    cursor: pointer;
    color: #fff;
    flex-shrink: 0;
  }

  &__logo {
    width: 30px;
    height: 30px;
    border-radius: 8px;
    background: linear-gradient(135deg, #4f8cff, #2f6bff);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    color: #fff;
    flex-shrink: 0;
  }

  &__brand-text {
    font-size: 15px;
    font-weight: 600;
    white-space: nowrap;
  }

  &__menu {
    flex: 1;
    border-right: none;
    background: transparent;
    overflow-y: auto;
    --el-menu-bg-color: transparent;
    --el-menu-text-color: #c3c9d5;
    --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
    --el-menu-active-color: #ffffff;
  }

  &__menu :deep(.el-menu-item) {
    margin: 4px 8px;
    border-radius: 8px;
  }

  &__menu :deep(.el-menu-item.is-active) {
    background: var(--ai-primary);
    color: #fff;
  }

  &__aside-footer {
    padding: 12px 18px;
    font-size: 12px;
    color: #6b7280;
    white-space: nowrap;
  }

  &__body {
    min-width: 0;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #fff;
    border-bottom: 1px solid var(--ai-border);
    padding: 0 20px;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    color: var(--ai-text);
  }

  &__username {
    font-size: 14px;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__main {
    background: var(--ai-bg);
    padding: 20px;
    overflow-y: auto;
  }

  &__mobile-menu {
    display: none;
  }
}

@media (max-width: 900px) {
  .shell__aside {
    display: none;
  }

  .shell__collapse {
    display: none;
  }

  .shell__mobile-menu {
    display: inline-flex;
  }

  .shell__main {
    padding: 12px;
  }

  .shell__username {
    display: none;
  }
}
</style>
