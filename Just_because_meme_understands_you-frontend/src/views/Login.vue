<template>
  <AuthPageLayout
    title="欢迎回来"
    desc="使用绑定邮箱登录，继续你的梗图旅程。"
    lead="登录后解锁收藏、发布与个性化推荐，让快乐常伴左右。"
  >
    <ui-form
      ref="loginFormRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="auth-form"
      @keyup.enter="handleSubmit"
    >
      <ui-form-item label="邮箱" prop="email">
        <vs-input
          v-model="form.email"
          block
          clearable
          placeholder="name@example.com"
          autocomplete="email"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-mail-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <ui-form-item label="密码" prop="password">
        <vs-input
          v-model="form.password"
          type="password"
          show-password
          block
          clearable
          placeholder="请输入密码"
          autocomplete="current-password"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-lock-2-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <div class="auth-form__row">
        <router-link to="/forgot-password" class="auth-form__link">忘记密码？</router-link>
      </div>

      <button
        type="button"
        class="auth-form__submit"
        :disabled="submitting"
        @click="handleSubmit"
      >
        <i
          v-if="submitting"
          class="ri-loader-4-line auth-form__spin"
          aria-hidden="true"
        />
        {{ submitting ? '登录中...' : '登录' }}
      </button>
    </ui-form>

    <template #footer>
      还没有账号？
      <router-link to="/register" class="auth-form__link auth-form__link--strong">立即注册</router-link>
    </template>
  </AuthPageLayout>
</template>

<script>
import { toast } from '@/utils/uiFeedback'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { POST_LOGIN_REDIRECT_KEY, resolveSafeRedirectPath } from '@/utils/authSession'
import AuthPageLayout from '@/components/auth/AuthPageLayout.vue'

export default {
  name: 'LoginPage',
  components: { AuthPageLayout },
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
            toast.success('登录成功')
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
            toast.error(msg)
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
.auth-form :deep(.ui-form-item) {
  margin-bottom: 16px;
}

.auth-form :deep(.ui-form-item__label) {
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--meme-text-secondary);
}

.auth-field {
  width: 100%;
}

.auth-field :deep(.vs-input__wrapper) {
  min-height: 46px;
  border-radius: 10px !important;
  background: var(--meme-bg-muted) !important;
  box-shadow: 0 0 0 1px transparent inset;
  transition: background 0.15s ease, box-shadow 0.15s ease;
}

.auth-field :deep(.vs-input__original) {
  width: 100% !important;
  min-height: 46px;
  padding: 0 40px 0 42px !important;
  border: none !important;
  background: transparent !important;
  color: var(--meme-text) !important;
  font-size: 14px !important;
  box-shadow: none !important;
}

.auth-field :deep(.vs-input__icon) {
  left: 12px !important;
  color: var(--meme-text-muted);
  background: transparent !important;
  box-shadow: none !important;
  transform: none !important;
}

.auth-field :deep(.vs-input.is-hovering .vs-input__wrapper),
.auth-field:hover :deep(.vs-input__wrapper) {
  background: color-mix(in srgb, var(--meme-bg-muted) 70%, var(--meme-bg-elevated)) !important;
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--meme-primary) 22%, var(--meme-border)) inset !important;
}

.auth-field :deep(.vs-input.is-focus .vs-input__wrapper) {
  background: var(--meme-bg-elevated) !important;
  box-shadow:
    0 0 0 1px var(--meme-primary) inset,
    0 0 0 3px var(--meme-focus-ring) !important;
}

.auth-field :deep(.vs-input.is-focus .vs-input__icon) {
  color: var(--meme-primary);
}

.auth-form__row {
  display: flex;
  justify-content: flex-end;
  margin: -4px 0 18px;
}

a.auth-form__link,
.auth-form__link {
  color: var(--meme-primary) !important;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

a.auth-form__link:hover,
.auth-form__link:hover {
  color: var(--meme-primary-dark) !important;
}

a.auth-form__link--strong,
.auth-form__link--strong {
  font-weight: 700;
  margin-left: 4px;
}

.auth-form__submit {
  width: 100%;
  height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, var(--meme-primary) 0%, var(--meme-primary-dark) 100%);
  box-shadow: 0 6px 16px var(--meme-focus-ring);
  transition: box-shadow 0.18s ease, filter 0.15s ease, transform 0.12s ease;
}

.auth-form__submit:hover:not(:disabled) {
  filter: brightness(1.04);
  box-shadow: 0 8px 20px var(--meme-focus-ring);
}

.auth-form__submit:active:not(:disabled) {
  transform: scale(0.985);
}

.auth-form__submit:disabled {
  opacity: 0.72;
  cursor: wait;
}

.auth-form__spin {
  display: inline-block;
  animation: auth-spin 0.8s linear infinite;
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
