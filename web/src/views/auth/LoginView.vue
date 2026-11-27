<template>
  <div class="login">
    <section class="login__brand">
      <div class="login__brand-inner">
        <div class="login__logo">AI</div>
        <h1>AI 面试练习</h1>
        <p class="login__slogan">没面试过？先在这里练一遍</p>
        <ul class="login__features">
          <li>
            <el-icon><Monitor /></el-icon>
            选方向，结合简历现场出题
          </li>
          <li>
            <el-icon><Microphone /></el-icon>
            文字或语音作答，即时反馈
          </li>
          <li>
            <el-icon><DataAnalysis /></el-icon>
            结构化复盘，知道下一步练什么
          </li>
        </ul>
        <p class="login__hint">
          演示账号：candidate / 123456<br />
          没有面试经验也能直接上手
        </p>
      </div>
    </section>

    <section class="login__panel">
      <div class="login__card">
        <h2>登录</h2>
        <p class="muted mb-4">欢迎回来，继续你的面试练习</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="w-full" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <div class="login__footer">
          <span class="muted">还没有账号？</span>
          <router-link :to="{ name: 'register' }">立即注册</router-link>
        </div>

        <el-divider>快速体验</el-divider>
        <div class="login__demo">
          <el-button
            v-for="account in demoAccounts"
            :key="account.username"
            text
            bg
            size="small"
            @click="fillDemo(account.username)"
          >
            {{ account.label }}
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { DataAnalysis, Lock, Monitor, Microphone, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const demoAccounts = [{ username: 'candidate', label: '练习者 candidate' }]

function fillDemo(username: string): void {
  form.username = username
  form.password = '123456'
}

async function submit(): Promise<void> {
  if (loading.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect && redirect.startsWith('/') ? redirect : { name: 'practice-home' })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  min-height: 100vh;
  background: #fff;

  &__brand {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 48px;
    color: #fff;
    background:
      radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.18), transparent 40%),
      linear-gradient(135deg, #1d3fb8 0%, #2f6bff 45%, #5b8cff 100%);
    overflow: hidden;
  }

  &__brand::after {
    content: '';
    position: absolute;
    inset: 0;
    background-image: linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
    background-size: 36px 36px;
    pointer-events: none;
  }

  &__brand-inner {
    position: relative;
    z-index: 1;
    max-width: 420px;
  }

  &__logo {
    width: 56px;
    height: 56px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.16);
    border: 1px solid rgba(255, 255, 255, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    font-weight: 700;
    margin-bottom: 24px;
    backdrop-filter: blur(6px);
  }

  h1 {
    font-size: 34px;
    letter-spacing: 1px;
  }

  &__slogan {
    margin-top: 12px;
    font-size: 16px;
    opacity: 0.9;
  }

  &__features {
    list-style: none;
    padding: 0;
    margin: 40px 0 0;

    li {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 16px;
      font-size: 15px;
      opacity: 0.95;
    }
  }

  &__hint {
    margin-top: 48px;
    font-size: 12px;
    line-height: 1.9;
    opacity: 0.7;
  }

  &__panel {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 32px;
  }

  &__card {
    width: 100%;
    max-width: 380px;
  }

  h2 {
    font-size: 26px;
    margin-bottom: 6px;
  }

  &__footer {
    display: flex;
    justify-content: center;
    gap: 6px;
    margin-top: 18px;
    font-size: 13px;
  }

  &__demo {
    display: flex;
    justify-content: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

@media (max-width: 900px) {
  .login {
    grid-template-columns: 1fr;
  }

  .login__brand {
    display: none;
  }
}
</style>
