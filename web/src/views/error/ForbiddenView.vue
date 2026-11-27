<template>
  <div class="forbidden">
    <el-icon :size="56" class="forbidden__icon"><Lock /></el-icon>
    <h2>没有访问权限</h2>
    <p class="muted">当前账号的角色无权访问该页面，请切换账号或联系管理员。</p>
    <el-button type="primary" class="mt-4" @click="router.push({ name: 'dashboard' })">
      回到工作台
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped lang="scss">
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;

  &__icon {
    color: var(--ai-warning);
    margin-bottom: 16px;
  }

  h2 {
    font-size: 20px;
    margin-bottom: 8px;
  }
}
</style>
