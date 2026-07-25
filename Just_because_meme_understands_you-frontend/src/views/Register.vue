<template>
  <AuthPageLayout
    title="创建账号"
    desc="使用邮箱注册，收藏你爱的梗图，发现今日快乐。"
    lead="注册后即可发布梗图、管理收藏夹，并体验 AI 搜梗带来的语境匹配。"
  >
    <ui-form
      ref="registerFormRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="auth-form"
    >
      <ui-form-item label="邮箱" prop="email">
        <vs-input
          v-model="form.email"
          block
          clearable
          placeholder="用于接收验证码"
          autocomplete="email"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-mail-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <ui-form-item label="邮箱验证码" prop="verificationCode">
        <div class="auth-form__code">
          <vs-input
            v-model="form.verificationCode"
            block
            clearable
            maxlength="6"
            placeholder="6 位验证码"
            class="auth-field"
          >
            <template #icon>
              <i class="ri-shield-keyhole-line" aria-hidden="true" />
            </template>
          </vs-input>
          <button
            type="button"
            class="auth-form__code-btn"
            :disabled="sendingCode || countdown > 0"
            @click="handleSendCode"
          >
            <i
              v-if="sendingCode"
              class="ri-loader-4-line auth-form__spin"
              aria-hidden="true"
            />
            <template v-if="countdown > 0">{{ countdown }}s</template>
            <template v-else-if="sendingCode">发送中</template>
            <template v-else>获取验证码</template>
          </button>
        </div>
      </ui-form-item>

      <ui-form-item label="密码" prop="password">
        <vs-input
          v-model="form.password"
          type="password"
          show-password
          block
          clearable
          placeholder="至少 6 位，建议数字 + 字母"
          autocomplete="new-password"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-lock-2-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <ui-form-item label="确认密码" prop="confirmPassword">
        <vs-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          block
          clearable
          placeholder="请再次输入密码"
          autocomplete="new-password"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-lock-password-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

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
        {{ submitting ? '注册中...' : '立即注册' }}
      </button>
    </ui-form>

    <template #footer>
      已有账号？
      <router-link to="/login" class="auth-form__link auth-form__link--strong">直接登录</router-link>
    </template>
  </AuthPageLayout>
</template>

<script>
import { toast } from '@/utils/uiFeedback'
import { sendRegisterCode, register } from '@/api/auth'
import AuthPageLayout from '@/components/auth/AuthPageLayout.vue'

export default {
  name: 'RegisterPage',
  components: { AuthPageLayout },
  data() {
    const validateConfirmPassword = (_rule, value, callback) => {
      if (!value) {
        callback(new Error('请再次输入密码'))
        return
      }
      if (value !== this.form.password) {
        callback(new Error('两次输入的密码不一致'))
        return
      }
      callback()
    }

    return {
      form: {
        email: '',
        verificationCode: '',
        password: '',
        confirmPassword: '',
      },
      submitting: false,
      sendingCode: false,
      countdown: 0,
      countdownTimer: null,
      rules: {
        email: [
          { required: true, message: '请输入邮箱', trigger: 'blur' },
          {
            type: 'email',
            message: '请输入正确的邮箱格式',
            trigger: ['blur', 'change'],
          },
        ],
        verificationCode: [
          { required: true, message: '请输入验证码', trigger: 'blur' },
          {
            pattern: /^\d{4,6}$/,
            message: '验证码格式不正确',
            trigger: ['blur', 'change'],
          },
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          {
            min: 6,
            message: '密码长度不少于 6 位',
            trigger: 'blur',
          },
        ],
        confirmPassword: [
          {
            validator: validateConfirmPassword,
            trigger: ['blur', 'change'],
          },
        ],
      },
    }
  },
  mounted() {
    try {
      const saved = localStorage.getItem('meme_register_code_limit')
      if (saved) {
        const parsed = JSON.parse(saved)
        const expireAt = parsed && parsed.expireAt
        const now = Date.now()
        if (expireAt && expireAt > now) {
          const secondsLeft = Math.ceil((expireAt - now) / 1000)
          this.startCountdown(secondsLeft)
        } else {
          localStorage.removeItem('meme_register_code_limit')
        }
      }
    } catch (_) {
      // ignore storage error
    }
  },
  beforeUnmount() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer)
      this.countdownTimer = null
    }
  },
  methods: {
    startCountdown(seconds) {
      const start = Number(seconds) || 60
      this.countdown = start

      if (this.countdownTimer) {
        clearInterval(this.countdownTimer)
      }

      this.countdownTimer = setInterval(() => {
        if (this.countdown <= 1) {
          this.countdown = 0
          clearInterval(this.countdownTimer)
          this.countdownTimer = null
          try {
            localStorage.removeItem('meme_register_code_limit')
          } catch (_) {
            // ignore storage error
          }
        } else {
          this.countdown -= 1
        }
      }, 1000)
    },
    handleSendCode() {
      if (this.sendingCode || this.countdown > 0) return

      const email = (this.form.email || '').trim()
      const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

      if (!email) {
        toast.error('请输入邮箱')
        return
      }
      if (!emailPattern.test(email)) {
        toast.error('请输入正确的邮箱格式')
        return
      }

      this.sendingCode = true

      sendRegisterCode(email)
        .then(({ retryAfter, message }) => {
          toast.success(message || '验证码已发送至你的邮箱')
          const now = Date.now()
          const ms = Number(retryAfter || 60) * 1000
          const expireAt = now + ms

          try {
            localStorage.setItem(
              'meme_register_code_limit',
              JSON.stringify({ expireAt })
            )
          } catch (_) {
            // ignore storage error
          }

          this.startCountdown(retryAfter)
        })
        .catch((err) => {
          const msg =
            (err && (err.message || err.msg)) ||
            '验证码发送失败，请稍后重试'
          toast.error(msg)
        })
        .finally(() => {
          this.sendingCode = false
        })
    },
    handleSubmit() {
      if (this.submitting) return

      this.$refs.registerFormRef.validate((valid) => {
        if (!valid) return
        this.submitting = true

        const payload = {
          email: this.form.email,
          password: this.form.password,
          confirmPassword: this.form.confirmPassword,
          verificationCode: this.form.verificationCode,
        }

        register(payload)
          .then((data) => {
            toast.success(data.message || '注册成功')

            this.$router.replace({
              path: '/login',
              query: { email: this.form.email },
            })
          })
          .catch((err) => {
            const msg =
              (err && (err.message || err.msg)) || '注册失败，请稍后重试'
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
  margin-bottom: 14px;
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

.auth-form__code {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: stretch;
}

.auth-form__code-btn {
  min-width: 108px;
  height: 46px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 28%, var(--meme-border));
  background: var(--meme-bg-elevated);
  color: var(--meme-primary);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.auth-form__code-btn:hover:not(:disabled) {
  background: var(--meme-primary-soft);
  border-color: var(--meme-primary);
  color: var(--meme-primary-dark);
}

.auth-form__code-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
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
  margin-top: 6px;
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

@media (max-width: 520px) {
  .auth-form__code {
    grid-template-columns: 1fr;
  }

  .auth-form__code-btn {
    width: 100%;
  }
}
</style>
