<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <img src="/icon.svg" alt="" />
        <h1>只因“梗”懂你 · 管理端</h1>
      </div>
      <p class="sub">使用已授予 ADMIN 或 SUPER_ADMIN 角色的用户账号登录</p>
      <a-form :model="form" layout="vertical" @submit-success="onSubmit">
        <a-form-item field="email" label="邮箱" :rules="[{ required: true, message: '请输入邮箱' }]">
          <a-input v-model="form.email" placeholder="admin@example.com" allow-clear />
        </a-form-item>
        <a-form-item field="password" label="密码" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model="form.password" placeholder="密码" allow-clear />
        </a-form-item>
        <a-form-item field="captchaCode" label="验证码" :rules="[{ required: true, message: '请输入验证码' }]">
          <div class="captcha-row">
            <a-input v-model="form.captchaCode" placeholder="验证码" />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-img"
              alt="captcha"
              @click="loadCaptcha"
            />
            <a-button v-else @click="loadCaptcha">获取</a-button>
          </div>
        </a-form-item>
        <a-button type="primary" html-type="submit" long :loading="loading">登录</a-button>
      </a-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCaptcha, login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const captchaImage = ref('')
const form = reactive({
  email: '',
  password: '',
  captchaId: '',
  captchaCode: '',
})

async function loadCaptcha() {
  try {
    const data = await fetchCaptcha()
    form.captchaId = data.captchaId
    captchaImage.value = data.imageBase64.startsWith('data:')
      ? data.imageBase64
      : `data:image/png;base64,${data.imageBase64}`
    form.captchaCode = ''
  } catch (e) {
    toastError(e.message || '验证码获取失败')
  }
}

async function onSubmit() {
  loading.value = true
  try {
    const data = await login({
      email: form.email,
      password: form.password,
      captchaId: form.captchaId,
      captchaCode: form.captchaCode,
    })
    const roles = Array.isArray(data.user && data.user.roles)
      ? data.user.roles
      : [String((data.user && data.user.role) || '').replace(/^ROLE_/, '')]
    if (!data.user || !roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).replace(/^ROLE_/, '')))) {
      toastError('当前账号不是管理员')
      await loadCaptcha()
      return
    }
    auth.setAuth({ token: data.token, user: data.user, rememberMe: true })
    toastSuccess('登录成功')
    const redirect = route.query.redirect ? String(route.query.redirect) : '/'
    router.replace(redirect.startsWith('/') ? redirect : '/')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '登录失败')
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (route.query.reason === 'forbidden') {
    toastError('需要管理员权限')
  }
  loadCaptcha()
})
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f6f7f9;
}

.login-card {
  width: 400px;
  padding: 32px;
  border: 1px solid #e9edf2;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(37, 52, 75, 0.045);
}

.login-card h1 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #1d2129;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.login-brand img {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
}

.login-brand h1 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.025em;
}

.sub {
  margin: 0 0 24px;
  color: #86909c;
  font-size: 13px;
}

.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.captcha-img {
  height: 36px;
  border: 1px solid #e5e9ef;
  border-radius: 7px;
  cursor: pointer;
}

@media (max-width: 480px) {
  .login-card {
    width: min(400px, 100%);
    padding: 26px 22px;
  }

  .login-brand h1 {
    font-size: 18px;
  }
}
</style>
