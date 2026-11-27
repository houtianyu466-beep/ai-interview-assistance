<template>
  <div class="empty-state">
    <el-empty :description="description || title">
      <template #image>
        <el-icon :size="56" class="empty-state__icon">
          <component :is="icon" />
        </el-icon>
      </template>
      <slot name="action" />
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: Component
  }>(),
  { icon: () => Document },
)
</script>

<style scoped lang="scss">
.empty-state {
  padding: var(--ai-space-6) 0;

  &__icon {
    color: #c9cdd4;
  }
}
</style>
