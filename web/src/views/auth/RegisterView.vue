<template>
  <div class="register">
    <div class="register__card">
      <div class="register__head">
        <div class="register__logo">AI</div>
        <div>
          <h2>创建账号</h2>
          <p class="muted">注册后即可开始模拟面试练习</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" size="large" placeholder="3-32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" size="large" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" size="large" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" size="large" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" size="large" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" size="large" placeholder="选填" />
        </el-form-item>

        <el-button type="primary" size="large" class="w-full" :loading="loading" @click="submit">
          注册并登录
        </el-button>
      </el-form>

      <div class="register__footer">
        <span class="muted">已有账号？</span>
        <router-link :to="{ name: 'login' }">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  email: '',
  phone: '',
})

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '长度需在 3-32 个字符之间', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度需在 6-64 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

async function submit(): Promise<void> {
  if (loading.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.register({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim() || undefined,
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
    })
    ElMessage.success('注册成功，已为你登录')
    await router.replace({ name: 'practice-home' })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.register {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px;
  background:
    radial-gradient(circle at 10% 10%, rgba(47, 107, 255, 0.12), transparent 45%),
    var(--ai-bg);

  &__card {
    width: 100%;
    max-width: 460px;
    background: #fff;
    border-radius: var(--ai-radius-lg);
    box-shadow: var(--ai-shadow-float);
    padding: 32px;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 24px;

    h2 {
      font-size: 22px;
      margin-bottom: 4px;
    }

    p {
      font-size: 13px;
    }
  }

  &__logo {
    width: 46px;
    height: 46px;
    border-radius: 12px;
    background: linear-gradient(135deg, #4f8cff, #2f6bff);
    color: #fff;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__footer {
    display: flex;
    justify-content: center;
    gap: 6px;
    margin-top: 16px;
    font-size: 13px;
  }
}
</style>
