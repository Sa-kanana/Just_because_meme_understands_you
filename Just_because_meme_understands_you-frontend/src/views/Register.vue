<template>
  <div class="register-page">
    <div class="register-hero">
      <h1 class="register-title">加入只因“梗”懂你</h1>
      <p class="register-subtitle">
        使用邮箱注册账号，收藏你爱的梗图，让灵感与快乐常伴左右。
      </p>
    </div>

    <el-card class="register-card" shadow="hover">
      <div class="register-card-header">
        <div>
          <h2 class="register-card-title">创建新账号</h2>
          <p class="register-card-subtitle">邮箱注册 · 更安全地管理你的梗图世界</p>
        </div>
        <div class="register-card-switch">
          已有账号？
          <router-link to="/login" class="link-button"> 直接登录 </router-link>
        </div>
      </div>

      <el-form
        ref="registerFormRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="register-form"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入常用邮箱，用于接收验证码"
            clearable
            autocomplete="email"
            size="large"
          />
        </el-form-item>

        <el-form-item label="邮箱验证码" prop="verificationCode">
          <div class="code-row">
            <el-input
              v-model="form.verificationCode"
              placeholder="请输入 6 位验证码"
              maxlength="6"
              clearable
              size="large"
            />
            <el-button
              type="primary"
              class="code-button"
              :disabled="sendingCode || countdown > 0"
              :loading="sendingCode"
              size="large"
              @click="handleSendCode"
            >
              <template v-if="countdown > 0">
                {{ countdown }}s 后可重发
              </template>
              <template v-else>
                发送验证码
              </template>
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="请设置登录密码，至少 6 位，建议包含数字与字母"
            clearable
            show-password
            autocomplete="new-password"
            size="large"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            placeholder="请再次输入密码"
            clearable
            show-password
            autocomplete="new-password"
            size="large"
          />
        </el-form-item>

        <div class="register-actions">
          <el-button
            type="primary"
            class="register-submit-button"
            :loading="submitting"
            :disabled="submitting"
            size="large"
            @click="handleSubmit"
          >
            {{ submitting ? '注册中...' : '立即注册' }}
          </el-button>
        </div>
      </el-form>

      <p class="register-hint">
        注册即表示你已阅读并同意本网站的相关使用条款与隐私政策。
      </p>
    </el-card>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { sendRegisterCode, register } from '@/api/auth'

export default {
  name: 'RegisterPage',
  data() {
    const validateConfirmPassword = (rule, value, callback) => {
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
    // 刷新页面后恢复发送验证码的冷却时间
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
    } catch (e) {
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
          } catch (e) {
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
      const emailPattern =
        /^[^\s@]+@[^\s@]+\.[^\s@]+$/

      if (!email) {
        ElMessage.error('请输入邮箱')
        return
      }
      if (!emailPattern.test(email)) {
        ElMessage.error('请输入正确的邮箱格式')
        return
      }

      this.sendingCode = true

      sendRegisterCode(email)
        .then(({ retryAfter, message }) => {
          ElMessage.success(message || '验证码已发送至你的邮箱')
          const now = Date.now()
          const ms = Number(retryAfter || 60) * 1000
          const expireAt = now + ms

          try {
            localStorage.setItem(
              'meme_register_code_limit',
              JSON.stringify({ expireAt })
            )
          } catch (e) {
            // ignore storage error
          }

          this.startCountdown(retryAfter)
        })
        .catch((err) => {
          const msg =
            (err && (err.message || err.msg)) ||
            '验证码发送失败，请稍后重试'
          ElMessage.error(msg)
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
            ElMessage.success(data.message || '注册成功')

            // 注册成功后跳转到登录页，并带上邮箱便于自动填充
            this.$router.replace({
              path: '/login',
              query: { email: this.form.email },
            })
          })
          .catch((err) => {
            const msg =
              (err && (err.message || err.msg)) || '注册失败，请稍后重试'
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
.register-page {
  min-height: calc(100vh - 56px);
  padding: 40px 24px 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--meme-gradient-page, radial-gradient(circle at top left, #e8f2fd 0%, #f9fafb 42%, #ffffff 100%));
}

.register-hero {
  max-width: 680px;
  text-align: center;
  margin-bottom: 32px;
}

.register-title {
  margin: 0 0 12px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #111827;
}

.register-subtitle {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.register-card {
  width: 100%;
  max-width: 520px;
  border-radius: var(--meme-radius-lg, 16px);
  box-shadow: var(--meme-shadow-card, 0 4px 12px rgba(49, 138, 239, 0.08)), 0 8px 24px rgba(15, 23, 42, 0.06);
  border: 1px solid var(--meme-border, #e5e7eb);
}

.register-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 8px;
}

.register-card-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: #111827;
}

.register-card-subtitle {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7280;
}

.register-card-switch {
  font-size: 13px;
  color: #6b7280;
}

.link-button {
  color: var(--meme-primary, #318AEF);
  font-weight: 500;
  text-decoration: none;
  transition: color 0.2s ease;
}
.link-button:hover {
  color: var(--meme-primary-dark, #2872d4);
}

.register-form {
  margin-top: 8px;
}

.code-row {
  display: grid;
  grid-template-columns: 1.4fr 0.9fr;
  gap: 10px;
}

.code-button {
  width: 100%;
}

.register-actions {
  margin-top: 8px;
  display: flex;
  justify-content: center;
}

.register-submit-button {
  width: 100%;
}

.register-hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}

@media (max-width: 600px) {
  .register-page {
    padding: 24px 16px 32px;
  }

  .register-card {
    max-width: 100%;
  }

  .code-row {
    grid-template-columns: 1.1fr 1fr;
  }
}
</style>

