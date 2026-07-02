<template>
  <div class="forgot-page">
    <div class="forgot-hero">
      <h1 class="forgot-title">找回密码，只因“梗”懂你</h1>
      <p class="forgot-subtitle">
        通过邮箱验证重置密码，继续安全地收藏你爱的梗图。
      </p>
    </div>

    <el-card class="forgot-card" shadow="hover">
      <!-- 步骤条 -->
      <el-steps :active="currentStep" finish-status="success" align-center class="forgot-steps">
        <el-step title="发送验证码" />
        <el-step title="核验验证码" />
        <el-step title="设置新密码" />
      </el-steps>

      <!-- 第一步：输入邮箱，发送验证码 -->
      <div v-show="currentStep === 1" class="step-panel">
        <div class="step-panel-header">
          <h2 class="step-title">输入绑定邮箱</h2>
          <p class="step-desc">我们将向该邮箱发送验证码，请确保邮箱可正常收信</p>
        </div>
        <el-form
          ref="step1FormRef"
          :model="step1"
          :rules="step1Rules"
          label-position="top"
          class="forgot-form"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="step1.email"
              placeholder="请输入注册时使用的邮箱"
              clearable
              autocomplete="email"
              size="large"
            />
          </el-form-item>
          <div class="code-row">
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
          <div class="step-actions">
            <el-button size="large" @click="$router.replace('/login')">返回登录</el-button>
            <el-button
              type="primary"
              size="large"
              :disabled="countdown <= 0"
              @click="goStep2"
            >
              下一步：输入验证码
            </el-button>
          </div>
        </el-form>
      </div>

      <!-- 第二步：输入验证码，获取 token -->
      <div v-show="currentStep === 2" class="step-panel">
        <div class="step-panel-header">
          <h2 class="step-title">输入邮箱验证码</h2>
          <p class="step-desc">请查收邮件中的验证码，5 分钟内有效</p>
        </div>
        <el-form
          ref="step2FormRef"
          :model="step2"
          :rules="step2Rules"
          label-position="top"
          class="forgot-form"
        >
          <el-form-item label="邮箱">
            <el-input :model-value="step1.email" disabled size="large" />
          </el-form-item>
          <el-form-item label="验证码" prop="code">
            <el-input
              v-model="step2.code"
              placeholder="请输入 6 位验证码"
              maxlength="6"
              clearable
              size="large"
            />
          </el-form-item>
          <div class="step-actions">
            <el-button size="large" @click="currentStep = 1">上一步</el-button>
            <el-button
              type="primary"
              size="large"
              :loading="verifying"
              :disabled="verifying"
              @click="handleVerifyCode"
            >
              {{ verifying ? '验证中...' : '验证并继续' }}
            </el-button>
          </div>
        </el-form>
      </div>

      <!-- 第三步：输入新密码，提交重置 -->
      <div v-show="currentStep === 3" class="step-panel">
        <div class="step-panel-header">
          <h2 class="step-title">设置新密码</h2>
          <p class="step-desc">请设置新密码，重置后需使用新密码登录</p>
        </div>
        <el-form
          ref="step3FormRef"
          :model="step3"
          :rules="step3Rules"
          label-position="top"
          class="forgot-form"
        >
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="step3.newPassword"
              placeholder="至少 6 位，建议包含数字与字母"
              clearable
              show-password
              autocomplete="new-password"
              size="large"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="step3.confirmPassword"
              placeholder="请再次输入新密码"
              clearable
              show-password
              autocomplete="new-password"
              size="large"
            />
          </el-form-item>
          <div class="step-actions">
            <el-button size="large" @click="currentStep = 2">上一步</el-button>
            <el-button
              type="primary"
              size="large"
              :loading="submitting"
              :disabled="submitting"
              @click="handleConfirmReset"
            >
              {{ submitting ? '提交中...' : '完成重置' }}
            </el-button>
          </div>
        </el-form>
      </div>

      <p class="forgot-hint">
        遇到问题？可前往
        <router-link to="/help" class="link-inline">帮助文档</router-link>
        或联系客服。
      </p>
    </el-card>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import {
  requestPasswordReset,
  verifyResetCode,
  confirmPasswordReset,
} from '@/api/auth'

export default {
  name: 'ForgotPasswordPage',
  data() {
    const validateConfirm = (rule, value, callback) => {
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
      step1: {
        email: '',
      },
      step2: {
        code: '',
      },
      step3: {
        newPassword: '',
        confirmPassword: '',
      },
      resetToken: '', // 第二步成功后存 token，第三步提交用
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
            pattern: /^\d{4,6}$/,
            message: '验证码格式不正确',
            trigger: ['blur', 'change'],
          },
        ],
      },
      step3Rules: {
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' },
        ],
        confirmPassword: [
          { validator: validateConfirm, trigger: ['blur', 'change'] },
        ],
      },
    }
  },
  mounted() {
    const emailFromQuery = this.$route.query.email
    if (emailFromQuery) {
      this.step1.email = String(emailFromQuery)
    }
    try {
      const saved = localStorage.getItem('meme_forgot_code_limit')
      if (saved) {
        const parsed = JSON.parse(saved)
        const expireAt = parsed && parsed.expireAt
        const now = Date.now()
        if (expireAt && expireAt > now) {
          const secondsLeft = Math.ceil((expireAt - now) / 1000)
          this.startCountdown(secondsLeft)
        } else {
          localStorage.removeItem('meme_forgot_code_limit')
        }
      }
    } catch (e) {
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
            localStorage.removeItem('meme_forgot_code_limit')
          } catch (_e) {
            /* ignore storage error */
          }
        } else {
          this.countdown -= 1
        }
      }, 1000)
    },
    handleSendCode() {
      if (this.sendingCode || this.countdown > 0) return
      this.$refs.step1FormRef.validate((valid) => {
        if (!valid) return
        const email = this.step1.email.trim()
        this.sendingCode = true
        requestPasswordReset(email)
          .then(({ retryAfter, message }) => {
            ElMessage.success(message || '验证码已发送至你的邮箱')
            const now = Date.now()
            const ms = Number(retryAfter || 60) * 1000
            try {
              localStorage.setItem(
                'meme_forgot_code_limit',
                JSON.stringify({ expireAt: now + ms })
              )
            } catch (_e) {
              /* ignore storage error */
            }
            this.startCountdown(retryAfter)
          })
          .catch((err) => {
            ElMessage.error(
              (err && (err.message || err.msg)) || '发送失败，请稍后重试'
            )
          })
          .finally(() => {
            this.sendingCode = false
          })
      })
    },
    goStep2() {
      this.$refs.step1FormRef.validate((valid) => {
        if (!valid) return
        if (this.countdown <= 0) {
          ElMessage.warning('请先点击「发送验证码」并查收邮件')
          return
        }
        this.currentStep = 2
        this.step2.code = ''
      })
    },
    handleVerifyCode() {
      this.$refs.step2FormRef.validate((valid) => {
        if (!valid) return
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
            ElMessage.success('验证成功，请设置新密码')
          })
          .catch((err) => {
            ElMessage.error(
              (err && (err.message || err.msg)) || '验证码错误或已过期'
            )
          })
          .finally(() => {
            this.verifying = false
          })
      })
    },
    handleConfirmReset() {
      this.$refs.step3FormRef.validate((valid) => {
        if (!valid) return
        if (!this.resetToken) {
          ElMessage.error('重置令牌已失效，请从第一步重新操作')
          return
        }
        this.submitting = true
        confirmPasswordReset({
          token: this.resetToken,
          newPassword: this.step3.newPassword,
        })
          .then(({ message }) => {
            ElMessage.success(message || '密码已重置，请使用新密码登录')
            this.$router.replace({
              path: '/login',
              query: { email: this.step1.email },
            })
          })
          .catch((err) => {
            ElMessage.error(
              (err && (err.message || err.msg)) || '重置失败，请重试'
            )
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
.forgot-page {
  min-height: calc(100vh - 56px);
  padding: 40px 24px 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(
    --meme-gradient-page,
    radial-gradient(circle at top left, #e8f2fd 0%, #f9fafb 42%, #ffffff 100%)
  );
}

.forgot-hero {
  max-width: 640px;
  text-align: center;
  margin-bottom: 32px;
}

.forgot-title {
  margin: 0 0 12px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #111827;
}

.forgot-subtitle {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.forgot-card {
  width: 100%;
  max-width: 460px;
  border-radius: var(--meme-radius-lg, 16px);
  box-shadow: var(
      --meme-shadow-card,
      0 4px 12px rgba(49, 138, 239, 0.08)
    ),
    0 8px 24px rgba(15, 23, 42, 0.06);
  border: 1px solid var(--meme-border, #e5e7eb);
}

.forgot-card :deep(.el-card__body) {
  padding: 24px 32px 28px;
}

.forgot-steps {
  margin-bottom: 24px;
}

.forgot-steps :deep(.el-step__title) {
  font-size: 13px;
}

.step-panel-header {
  margin-bottom: 16px;
}

.step-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.step-desc {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.forgot-form {
  margin-top: 8px;
}

.code-row {
  margin-bottom: 16px;
}

.code-button {
  width: 100%;
}

.step-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.step-actions .el-button {
  flex: 1;
}

.link-inline {
  color: var(--meme-primary, #318aef);
  text-decoration: none;
}
.link-inline:hover {
  color: var(--meme-primary-dark, #2872d4);
}

.forgot-hint {
  margin: 20px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}

@media (max-width: 600px) {
  .forgot-page {
    padding: 24px 16px 32px;
  }
  .forgot-card {
    max-width: 100%;
  }
}
</style>
