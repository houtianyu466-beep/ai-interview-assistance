<template>
  <div class="page-header">
    <div class="page-header__main">
      <el-button v-if="back" text class="page-header__back" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <div>
        <h2 class="page-header__title">{{ title }}</h2>
        <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const props = defineProps<{
  title: string
  subtitle?: string
  back?: boolean
}>()

const router = useRouter()

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'dashboard' })
  }
}

// 让 props 在模板中被使用（避免 noUnusedLocals 误报）
void props
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ai-space-4);
  margin-bottom: var(--ai-space-4);

  &__main {
    display: flex;
    align-items: flex-start;
    gap: var(--ai-space-2);
  }

  &__back {
    margin-top: 2px;
  }

  &__title {
    font-size: 20px;
    font-weight: 600;
    line-height: 1.4;
  }

  &__subtitle {
    margin-top: var(--ai-space-1);
    font-size: 13px;
    color: var(--ai-text-secondary);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
    flex-shrink: 0;
  }
}
</style>
