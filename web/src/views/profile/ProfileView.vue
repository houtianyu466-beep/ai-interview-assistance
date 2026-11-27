<template>
  <div class="profile">
    <PageHeader title="个人中心" subtitle="维护个人资料与登录密码" />

    <el-row :gutter="16">
      <el-col :xs="24" :lg="7">
        <div class="card account-card">
          <el-avatar :size="72" :src="auth.user?.avatar" class="account-card__avatar">
            {{ avatarText }}
          </el-avatar>
          <h3 class="account-card__name">{{ auth.displayName }}</h3>
          <StatusTag kind="role" :status="auth.role" />
          <el-descriptions :column="1" class="mt-4">
            <el-descriptions-item label="用户名">{{ auth.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDateTime(auth.user?.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">{{ formatDateTime(auth.user?.lastLoginAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>

      <el-col :xs="24" :lg="17">
        <div class="card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本资料" name="profile">
              <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="90px" class="profile__form">
                <el-form-item label="真实姓名" prop="realName">
                  <el-input v-model="profileForm.realName" maxlength="64" placeholder="请输入真实姓名" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="profileForm.email" maxlength="128" placeholder="用于接收面试通知" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="profileForm.phone" maxlength="11" placeholder="11 位手机号" />
                </el-form-item>
                <el-form-item label="头像地址" prop="avatar">
                  <el-input v-model="profileForm.avatar" maxlength="255" placeholder="https://…（选填）" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="修改密码" name="password">
              <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px" class="profile__form">
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6-64 位" />
                </el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword">
                  <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="savingPassword" @click="savePassword">修改密码</el-button>
                  <span class="muted profile__hint">修改后下次登录请使用新密码</span>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { changePassword, updateProfile } from '@/api/users'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()

const activeTab = ref<'profile' | 'password'>('profile')
const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const savingProfile = ref(false)
const savingPassword = ref(false)

const profileForm = reactive({
  realName: '',
  email: '',
  phone: '',
  avatar: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const avatarText = computed(() => auth.displayName.slice(0, 1).toUpperCase())

function syncProfile(): void {
  profileForm.realName = auth.user?.realName ?? ''
  profileForm.email = auth.user?.email ?? ''
  profileForm.phone = auth.user?.phone ?? ''
  profileForm.avatar = auth.user?.avatar ?? ''
}

watch(() => auth.user, syncProfile, { immediate: true })

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void): void => {
  if (!value) return callback(new Error('请再次输入新密码'))
  if (value !== passwordForm.newPassword) return callback(new Error('两次输入的密码不一致'))
  callback()
}

const profileRules: FormRules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度需在 6-64 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
}

async function saveProfile(): Promise<void> {
  if (savingProfile.value) return
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    await updateProfile({
      realName: profileForm.realName.trim() || undefined,
      email: profileForm.email.trim() || undefined,
      phone: profileForm.phone.trim() || undefined,
      avatar: profileForm.avatar.trim() || undefined,
    })
    await auth.fetchProfile()
    ElMessage.success('资料已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '保存失败')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword(): Promise<void> {
  if (savingPassword.value) return
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value?.clearValidate()
    ElMessage.success('密码已修改')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '修改失败')
  } finally {
    savingPassword.value = false
  }
}
</script>

<style scoped lang="scss">
.account-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-top: 30px;

  &__avatar {
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 24px;
    font-weight: 600;
  }

  &__name {
    margin: 12px 0 8px;
    font-size: 18px;
  }
}

.profile {
  &__form {
    max-width: 520px;
    margin-top: 8px;
  }

  &__hint {
    margin-left: 12px;
    font-size: 12px;
  }
}
</style>
