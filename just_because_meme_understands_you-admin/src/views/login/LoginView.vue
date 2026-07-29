<template>
  <div class="login-page">
    <div class="login-card">
      <h1>只因梗懂你 · 管理端</h1>
      <p class="sub">仅 ROLE_ADMIN 可登录</p>
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
    if (!data.user || String(data.user.role || '') !== 'ROLE_ADMIN') {
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(22, 93, 255, 0.12), transparent 40%),
    radial-gradient(circle at 80% 0%, rgba(14, 180, 126, 0.12), transparent 35%),
    linear-gradient(160deg, #f7f8fa, #eef2f8);
}
.login-card {
  width: 400px;
  padding: 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(29, 33, 41, 0.08);
}
.login-card h1 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #1d2129;
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
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid #e5e6eb;
}
</style>
