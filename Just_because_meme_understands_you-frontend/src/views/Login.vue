<template>
  <div class="login-page">
    <div class="login-hero">
      <h1 class="login-title">欢迎回来，梗友</h1>
      <p class="login-subtitle">
        使用绑定邮箱登录，解锁更多只因“梗”懂你的个性化体验。
      </p>
    </div>

    <el-card class="login-card" shadow="hover">
      <div class="login-card-header">
        <div>
          <h2 class="login-card-title">账号登录</h2>
          <p class="login-card-subtitle">
            使用邮箱 + 密码登录，继续探索只因“梗”懂你
          </p>
        </div>
        <div class="login-card-switch">
          <span class="login-card-switch-text">还没有账号？</span>
          <router-link to="/register" class="link-button">立即注册</router-link>
        </div>
      </div>

      <el-form
        ref="loginFormRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-form"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱"
            clearable
            autocomplete="email"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            clearable
            show-password
            autocomplete="current-password"
            size="large"
          />
        </el-form-item>

        <div class="login-extra-row">
          <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
        </div>

        <div class="login-actions">
          <el-button
            type="primary"
            class="login-submit-button"
            :loading="submitting"
            :disabled="submitting"
            size="large"
            @click="handleSubmit"
          >
            {{ submitting ? '登录中...' : '登录' }}
          </el-button>
        </div>
      </el-form>

      <p class="login-hint">
        登录即表示你同意本网站的相关使用条款。
      </p>
    </el-card>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { POST_LOGIN_REDIRECT_KEY, resolveSafeRedirectPath } from '@/utils/authSession'

export default {
  name: 'LoginPage',
  data() {
    return {
      form: {
        email: '',
        password: '',
        loginType: 'email',
      },
      submitting: false,
      rules: {
        email: [
          { required: true, message: '请输入邮箱', trigger: 'blur' },
          {
            type: 'email',
            message: '请输入正确的邮箱格式',
            trigger: ['blur', 'change'],
          },
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' },
        ],
      },
    }
  },
  mounted() {
    // 如果从注册页带了邮箱过来，自动填充，减少重复输入
    const emailFromQuery = this.$route.query.email
    if (emailFromQuery) {
      this.form.email = String(emailFromQuery)
    }
  },
  methods: {
    handleSubmit() {
      if (this.submitting) return

      this.$refs.loginFormRef.validate((valid) => {
        if (!valid) return
        this.submitting = true

        const payload = {
          email: this.form.email,
          password: this.form.password,
          loginType: this.form.loginType,
        }

        login(payload)
          .then(({ token, user }) => {
            useAuthStore().setAuth({
              token,
              user,
            })
            ElMessage.success('登录成功')
            const redirectFromQuery = this.$route.query.redirect
            let redirectFromStorage = ''
            try {
              redirectFromStorage = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY) || ''
              sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
            } catch (_) {
              // ignore
            }
            const redirect = resolveSafeRedirectPath(redirectFromQuery || redirectFromStorage)
            this.$router.replace(redirect)
          })
          .catch((err) => {
            const msg =
              (err && (err.message || err.msg)) || '登录失败，请稍后重试'
            ElMessage.error(msg)
          })
          .finally(() => {
            this.submitting = false
          })
      })
    },
  },
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - 56px);
  padding: 40px 24px 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--meme-gradient-page);
}

.login-hero {
  max-width: 640px;
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  margin: 0 0 12px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--meme-text);
}

.login-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--meme-text-secondary);
}

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: var(--meme-radius-lg);
  box-shadow: var(--meme-shadow-card), var(--meme-shadow-soft);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
}

.login-card :deep(.el-card__body) {
  padding: 24px 32px 28px;
}

.login-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.login-card-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--meme-text);
}

.login-card-subtitle {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--meme-text-secondary);
}

.login-card-switch {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--meme-text-secondary);
  white-space: nowrap;
}

.link-button {
  color: var(--meme-primary);
  font-weight: 500;
  text-decoration: none;
  transition: color 0.2s ease;
}
.link-button:hover {
  color: var(--meme-primary-dark);
}

.login-card-switch-text {
  line-height: 1;
}

.login-form {
  margin-top: 16px;
}

.login-extra-row {
  margin-top: 8px;
  margin-bottom: 8px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  font-size: 13px;
  color: var(--meme-text-secondary);
}

.forgot-link {
  color: var(--meme-primary);
  text-decoration: none;
}
.forgot-link:hover {
  color: var(--meme-primary-dark);
}

.login-actions {
  margin-top: 8px;
  display: flex;
  justify-content: center;
}

.login-submit-button {
  width: 100%;
}

.login-hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--meme-text-muted);
  text-align: center;
}

@media (max-width: 600px) {
  .login-page {
    padding: 24px 16px 32px;
  }

  .login-card {
    max-width: 100%;
  }
}
</style>

