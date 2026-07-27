<template>
  <AuthPageLayout
    :title="panelTitle"
    :desc="panelDesc"
    lead="通过邮箱验证重置密码，继续安全地收藏你爱的梗图。"
  >
    <nav class="forgot-steps" aria-label="找回密码进度">
      <div
        v-for="(item, index) in stepLabels"
        :key="item"
        class="forgot-steps__item"
        :class="{
          'is-active': currentStep === index + 1,
          'is-done': currentStep > index + 1,
        }"
      >
        <span class="forgot-steps__index" aria-hidden="true">{{ index + 1 }}</span>
        <span class="forgot-steps__label">{{ item }}</span>
      </div>
    </nav>

    <!-- 步骤 1：发送验证码 -->
    <ui-form
      v-show="currentStep === 1"
      ref="step1FormRef"
      :model="step1"
      :rules="step1Rules"
      label-position="top"
      class="auth-form"
    >
      <ui-form-item label="绑定邮箱" prop="email">
        <vs-input
          v-model="step1.email"
          block
          clearable
          placeholder="注册时使用的邮箱"
          autocomplete="email"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-mail-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <button
        type="button"
        class="auth-form__submit"
        :disabled="sendingCode || countdown > 0"
        @click="handleSendCode"
      >
        <i
          v-if="sendingCode"
          class="ri-loader-4-line auth-form__spin"
          aria-hidden="true"
        />
        <template v-if="countdown > 0">{{ countdown }}s 后可重发</template>
        <template v-else-if="sendingCode">发送中...</template>
        <template v-else>发送验证码</template>
      </button>

      <button
        type="button"
        class="auth-form__secondary"
        :disabled="countdown <= 0"
        @click="goStep2"
      >
        已收到验证码，下一步
      </button>
    </ui-form>

    <!-- 步骤 2：核验验证码 -->
    <ui-form
      v-show="currentStep === 2"
      ref="step2FormRef"
      :model="step2"
      :rules="step2Rules"
      label-position="top"
      class="auth-form"
    >
      <ui-form-item label="邮箱">
        <vs-input :model-value="step1.email" block disabled class="auth-field">
          <template #icon>
            <i class="ri-mail-check-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <ui-form-item label="邮箱验证码" prop="code">
        <div class="auth-form__code">
          <vs-input
            v-model="step2.code"
            block
            clearable
            maxlength="6"
            placeholder="6 位验证码"
            class="auth-field"
            @keyup.enter="handleVerifyCode"
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
            <template v-if="countdown > 0">{{ countdown }}s</template>
            <template v-else-if="sendingCode">发送中</template>
            <template v-else>重新发送</template>
          </button>
        </div>
      </ui-form-item>

      <button
        type="button"
        class="auth-form__submit"
        :disabled="verifying"
        @click="handleVerifyCode"
      >
        <i
          v-if="verifying"
          class="ri-loader-4-line auth-form__spin"
          aria-hidden="true"
        />
        {{ verifying ? '验证中...' : '验证并继续' }}
      </button>

      <button type="button" class="auth-form__ghost" @click="currentStep = 1">
        返回上一步
      </button>
    </ui-form>

    <!-- 步骤 3：设置新密码 -->
    <ui-form
      v-show="currentStep === 3"
      ref="step3FormRef"
      :model="step3"
      :rules="step3Rules"
      label-position="top"
      class="auth-form"
    >
      <ui-form-item label="新密码" prop="newPassword">
        <vs-input
          v-model="step3.newPassword"
          type="password"
          show-password
          block
          clearable
          placeholder="8～72 位，需同时含字母和数字"
          autocomplete="new-password"
          class="auth-field"
        >
          <template #icon>
            <i class="ri-lock-2-line" aria-hidden="true" />
          </template>
        </vs-input>
      </ui-form-item>

      <ui-form-item label="确认新密码" prop="confirmPassword">
        <vs-input
          v-model="step3.confirmPassword"
          type="password"
          show-password
          block
          clearable
          placeholder="请再次输入新密码"
          autocomplete="new-password"
          class="auth-field"
          @keyup.enter="handleConfirmReset"
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
        @click="handleConfirmReset"
      >
        <i
          v-if="submitting"
          class="ri-loader-4-line auth-form__spin"
          aria-hidden="true"
        />
        {{ submitting ? '提交中...' : '完成重置' }}
      </button>

      <button type="button" class="auth-form__ghost" @click="currentStep = 2">
        返回上一步
      </button>
    </ui-form>

    <template #footer>
      想起密码了？
      <router-link to="/login" class="auth-form__link auth-form__link--strong">返回登录</router-link>
      <span class="auth-form__sep">·</span>
      <router-link to="/help" class="auth-form__link">帮助文档</router-link>
    </template>
  </AuthPageLayout>
</template>

<script>
import { toast } from '@/utils/uiFeedback'
import {
  requestPasswordReset,
  verifyResetCode,
  confirmPasswordReset,
} from '@/api/auth'
import AuthPageLayout from '@/components/auth/AuthPageLayout.vue'

const CODE_LIMIT_KEY = 'meme_forgot_code_limit'

export default {
  name: 'ForgotPasswordPage',
  components: { AuthPageLayout },
  data() {
    const validateNewPassword = (_rule, value, callback) => {
      const raw = value != null ? String(value) : ''
      if (!raw) {
        callback(new Error('请输入新密码'))
        return
      }
      if (raw.length < 8 || raw.length > 72) {
        callback(new Error('密码长度需在 8～72 个字符之间'))
        return
      }
      if (!/[A-Za-z]/.test(raw) || !/\d/.test(raw)) {
        callback(new Error('密码需同时包含字母和数字'))
        return
      }
      callback()
    }
    const validateConfirm = (_rule, value, callback) => {
      if (!value) {
        callback(new Error('请再次输入新密码'))
        return
      }
      if (value !== this.step3.newPassword) {
        callback(new Error('两次输入的密码不一致'))
        return
      }
      callback()
    }
    return {
      currentStep: 1,
      stepLabels: ['发送验证码', '核验验证码', '设置新密码'],
      step1: { email: '' },
      step2: { code: '' },
      step3: { newPassword: '', confirmPassword: '' },
      resetToken: '',
      sendingCode: false,
      countdown: 0,
      countdownTimer: null,
      verifying: false,
      submitting: false,
      step1Rules: {
        email: [
          { required: true, message: '请输入邮箱', trigger: 'blur' },
          {
            type: 'email',
            message: '请输入正确的邮箱格式',
            trigger: ['blur', 'change'],
          },
        ],
      },
      step2Rules: {
        code: [
          { required: true, message: '请输入验证码', trigger: 'blur' },
          {
            pattern: /^\d{6}$/,
            message: '请输入 6 位数字验证码',
            trigger: ['blur', 'change'],
          },
        ],
      },
      step3Rules: {
        newPassword: [
          { validator: validateNewPassword, trigger: ['blur', 'change'] },
        ],
        confirmPassword: [
          { validator: validateConfirm, trigger: ['blur', 'change'] },
        ],
      },
    }
  },
  computed: {
    panelTitle() {
      if (this.currentStep === 2) return '输入验证码'
      if (this.currentStep === 3) return '设置新密码'
      return '找回密码'
    },
    panelDesc() {
      if (this.currentStep === 2) return '请查收邮件中的 6 位验证码，5 分钟内有效'
      if (this.currentStep === 3) return '重置成功后需使用新密码重新登录'
      return '先验证绑定邮箱，再设置新密码'
    },
  },
  mounted() {
    const emailFromQuery = this.$route.query.email
    if (emailFromQuery) {
      this.step1.email = String(emailFromQuery)
    }
    try {
      const saved = localStorage.getItem(CODE_LIMIT_KEY)
      if (saved) {
        const parsed = JSON.parse(saved)
        const expireAt = parsed && parsed.expireAt
        const now = Date.now()
        if (expireAt && expireAt > now) {
          this.startCountdown(Math.ceil((expireAt - now) / 1000))
        } else {
          localStorage.removeItem(CODE_LIMIT_KEY)
        }
      }
    } catch (_) {
      // ignore
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
      if (this.countdownTimer) clearInterval(this.countdownTimer)
      this.countdownTimer = setInterval(() => {
        if (this.countdown <= 1) {
          this.countdown = 0
          clearInterval(this.countdownTimer)
          this.countdownTimer = null
          try {
            localStorage.removeItem(CODE_LIMIT_KEY)
          } catch (_) {
            // ignore
          }
        } else {
          this.countdown -= 1
        }
      }, 1000)
    },
    handleSendCode() {
      if (this.sendingCode || this.countdown > 0) return
      const run = () => {
        this.sendingCode = true
        requestPasswordReset(this.step1.email.trim())
          .then(({ retryAfter, message }) => {
            toast.success(message || '验证码已发送至你的邮箱')
            const ms = Number(retryAfter || 60) * 1000
            try {
              localStorage.setItem(
                CODE_LIMIT_KEY,
                JSON.stringify({ expireAt: Date.now() + ms })
              )
            } catch (_) {
              // ignore
            }
            this.startCountdown(retryAfter)
          })
          .catch((err) => {
            toast.error((err && (err.message || err.msg)) || '发送失败，请稍后重试')
          })
          .finally(() => {
            this.sendingCode = false
          })
      }
      if (this.currentStep === 1 && this.$refs.step1FormRef) {
        this.$refs.step1FormRef.validate((valid) => {
          if (!valid) return
          run()
        })
        return
      }
      if (!this.step1.email) {
        toast.error('请输入邮箱')
        return
      }
      run()
    },
    goStep2() {
      this.$refs.step1FormRef.validate((valid) => {
        if (!valid) return
        if (this.countdown <= 0) {
          toast.warning('请先发送验证码并查收邮件')
          return
        }
        this.currentStep = 2
        this.step2.code = ''
      })
    },
    handleVerifyCode() {
      const form = this.$refs.step2FormRef
      if (!form || typeof form.validate !== 'function') {
        toast.error('表单未就绪，请刷新页面重试')
        return
      }
      form.validate((valid, errors) => {
        if (!valid) {
          toast.warning((errors && errors[0]) || '请填写正确的验证码')
          return
        }
        this.verifying = true
        verifyResetCode({
          email: this.step1.email.trim(),
          code: this.step2.code.trim(),
        })
          .then(({ token }) => {
            this.resetToken = token
            this.currentStep = 3
            this.step3.newPassword = ''
            this.step3.confirmPassword = ''
            toast.success('验证成功，请设置新密码')
          })
          .catch((err) => {
            toast.error((err && (err.message || err.msg)) || '验证码错误或已过期')
          })
          .finally(() => {
            this.verifying = false
          })
      })
    },
    handleConfirmReset() {
      if (this.submitting) return

      const newPassword = this.step3.newPassword != null ? String(this.step3.newPassword) : ''
      const confirmPassword =
        this.step3.confirmPassword != null ? String(this.step3.confirmPassword) : ''

      if (!newPassword) {
        toast.warning('请输入新密码')
        return
      }
      if (newPassword.length < 8 || newPassword.length > 72) {
        toast.warning('密码长度需在 8～72 个字符之间')
        return
      }
      if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
        toast.warning('密码需同时包含字母和数字')
        return
      }
      if (!confirmPassword) {
        toast.warning('请再次输入新密码')
        return
      }
      if (confirmPassword !== newPassword) {
        toast.warning('两次输入的密码不一致')
        return
      }
      if (!this.resetToken) {
        toast.error('重置令牌已失效，请从第一步重新操作')
        this.currentStep = 1
        return
      }

      this.submitting = true
      confirmPasswordReset({
        token: this.resetToken,
        newPassword,
      })
        .then(({ message }) => {
          toast.success(message || '密码已重置，请使用新密码登录')
          try {
            localStorage.removeItem(CODE_LIMIT_KEY)
          } catch (_) {
            // ignore
          }
          this.$router.replace({
            path: '/login',
            query: { email: this.step1.email },
          })
        })
        .catch((err) => {
          toast.error((err && (err.message || err.msg)) || '重置失败，请重试')
        })
        .finally(() => {
          this.submitting = false
        })
    },
  },
}
</script>

<style scoped>
.forgot-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.forgot-steps__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  color: var(--meme-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.forgot-steps__item.is-active,
.forgot-steps__item.is-done {
  color: var(--meme-primary);
  border-color: color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border));
  background: var(--meme-primary-soft);
}

.forgot-steps__index {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  border: 1px solid currentColor;
}

.forgot-steps__item.is-active .forgot-steps__index,
.forgot-steps__item.is-done .forgot-steps__index {
  background: var(--meme-primary);
  color: #fff;
  border-color: var(--meme-primary);
}

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

.auth-field :deep(.vs-input.is-focus .vs-input__wrapper) {
  background: var(--meme-bg-elevated) !important;
  box-shadow:
    0 0 0 1px var(--meme-primary) inset,
    0 0 0 3px var(--meme-focus-ring) !important;
}

.auth-form__code {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.auth-form__code .auth-field {
  flex: 1;
  min-width: 0;
}

.auth-form__code-btn {
  flex-shrink: 0;
  min-width: 108px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-elevated);
  color: var(--meme-primary);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.auth-form__code-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.auth-form__submit,
.auth-form__secondary,
.auth-form__ghost {
  width: 100%;
  height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: filter 0.15s ease, transform 0.12s ease, opacity 0.15s ease;
}

.auth-form__submit {
  border: none;
  color: #fff;
  background: linear-gradient(135deg, var(--meme-primary) 0%, var(--meme-primary-dark) 100%);
  box-shadow: 0 6px 16px var(--meme-focus-ring);
}

.auth-form__submit:hover:not(:disabled) {
  filter: brightness(1.04);
}

.auth-form__submit:disabled {
  opacity: 0.72;
  cursor: wait;
}

.auth-form__secondary {
  margin-top: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-elevated);
  color: var(--meme-text);
  font-weight: 650;
  font-size: 14px;
}

.auth-form__secondary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.auth-form__ghost {
  margin-top: 10px;
  border: none;
  background: transparent;
  color: var(--meme-text-secondary);
  font-weight: 600;
  font-size: 13px;
}

.auth-form__ghost:hover {
  color: var(--meme-primary);
}

.auth-form__spin {
  display: inline-block;
  animation: forgot-spin 0.8s linear infinite;
}

@keyframes forgot-spin {
  to {
    transform: rotate(360deg);
  }
}

a.auth-form__link,
.auth-form__link {
  color: var(--meme-primary) !important;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

a.auth-form__link--strong,
.auth-form__link--strong {
  font-weight: 700;
  margin-left: 4px;
}

.auth-form__sep {
  margin: 0 8px;
  color: var(--meme-text-muted);
}
</style>
