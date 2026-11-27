import { ref } from 'vue'
import { defineStore } from 'pinia'

const COLLAPSE_KEY = 'ai-interview:sidebar-collapsed'

/** 全局 UI 偏好：侧边栏折叠、移动端抽屉 */
export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')
  const mobileMenuVisible = ref(false)

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem(COLLAPSE_KEY, sidebarCollapsed.value ? '1' : '0')
  }

  function openMobileMenu(): void {
    mobileMenuVisible.value = true
  }

  function closeMobileMenu(): void {
    mobileMenuVisible.value = false
  }

  return { sidebarCollapsed, mobileMenuVisible, toggleSidebar, openMobileMenu, closeMobileMenu }
})
