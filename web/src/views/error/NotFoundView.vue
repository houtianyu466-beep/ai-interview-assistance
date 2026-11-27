<template>
  <div class="not-found">
    <div class="not-found__code">404</div>
    <h2>页面不存在</h2>
    <p class="muted">你访问的页面可能已被移除，或地址输入有误。</p>
    <div class="mt-4 flex-center gap-2">
      <el-button type="primary" @click="router.push({ name: 'dashboard' })">回到工作台</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped lang="scss">
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;

  &__code {
    font-size: 72px;
    font-weight: 700;
    line-height: 1;
    background: linear-gradient(135deg, #2f6bff, #8b5cf6);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    margin-bottom: 12px;
  }

  h2 {
    font-size: 20px;
    margin-bottom: 8px;
  }
}
</style>
