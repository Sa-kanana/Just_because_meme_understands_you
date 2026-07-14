<template>
  <div class="settings-page">
    <header class="settings-hero">
      <h1 class="settings-title">账号设置</h1>
      <p class="settings-lead">管理个人资料与账号安全，保护你的梗位。</p>
    </header>

    <div v-loading="pageLoading" class="settings-body">
      <aside class="settings-nav" aria-label="设置分区">
        <button
          type="button"
          class="settings-nav__item"
          :class="{ 'is-active': activeTab === 'profile' }"
          @click="switchTab('profile')"
        >
          <i class="ri-user-settings-line" aria-hidden="true" />
          个人资料
        </button>
        <button
          type="button"
          class="settings-nav__item"
          :class="{ 'is-active': activeTab === 'security' }"
          @click="switchTab('security')"
        >
          <i class="ri-shield-keyhole-line" aria-hidden="true" />
          账号安全
        </button>
      </aside>

      <section class="settings-panel">
        <template v-if="activeTab === 'profile'">
          <h2 class="settings-panel__title">个人资料</h2>
          <p class="settings-panel__desc">这些信息会展示在个人主页与发布的梗中。</p>

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-position="top"
            class="settings-form"
          >
            <div class="avatar-row">
              <el-avatar :size="72" :src="profileForm.avatar" class="avatar-preview">
                {{ avatarFallback }}
              </el-avatar>
              <div class="avatar-actions">
                <el-upload
                  :show-file-list="false"
                  :before-upload="beforeAvatarUpload"
                  accept="image/jpeg,image/png,image/webp"
                  :http-request="handleAvatarUpload"
                >
                  <el-button :loading="avatarUploading">更换头像</el-button>
                </el-upload>
                <p class="avatar-hint">支持 jpg / png / webp，建议正方形</p>
              </div>
            </div>

            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" maxlength="50" show-word-limit clearable />
            </el-form-item>
            <el-form-item label="个性签名" prop="signature">
              <el-input
                v-model="profileForm.signature"
                type="textarea"
                :rows="3"
                maxlength="255"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="profileForm.gender">
                <el-radio :label="0">保密</el-radio>
                <el-radio :label="1">男</el-radio>
                <el-radio :label="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="生日" prop="birthday">
              <el-date-picker
                v-model="profileForm.birthday"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择生日"
                clearable
                style="width: 100%"
              />
            </el-form-item>
            <div class="form-actions">
              <el-button type="primary" :loading="profileSaving" @click="saveProfile">
                保存资料
              </el-button>
            </div>
          </el-form>
        </template>

        <template v-else>
          <div class="sec-head">
            <div>
              <h2 class="settings-panel__title">账号安全</h2>
              <p class="settings-panel__desc">保护登录凭证与设备会话。敏感操作完成后需重新登录。</p>
            </div>
            <button
              type="button"
              class="sec-refresh"
              :disabled="securityRefreshing"
              :title="securityRefreshing ? '刷新中' : '刷新安全状态'"
              @click="refreshSecurity"
            >
              <i class="ri-refresh-line" :class="{ 'is-spinning': securityRefreshing }" aria-hidden="true" />
              刷新
            </button>
          </div>

          <div class="sec-health" :class="'sec-health--' + securityLevel.key">
            <div class="sec-health__icon" aria-hidden="true">
              <i :class="securityLevel.icon" />
            </div>
            <div class="sec-health__body">
              <div class="sec-health__top">
                <span class="sec-health__label">安全评级</span>
                <span class="sec-health__badge">{{ securityLevel.label }}</span>
              </div>
              <p class="sec-health__text">{{ securityLevel.hint }}</p>
              <div class="sec-health__meter" role="meter" :aria-valuenow="securityScore" aria-valuemin="0" aria-valuemax="100">
                <span class="sec-health__meter-bar" :style="{ width: securityScore + '%' }" />
              </div>
            </div>
          </div>

          <div class="sec-section">
            <h3 class="sec-section__title">登录方式</h3>
            <ul class="sec-list">
              <li class="sec-item">
                <div class="sec-item__icon sec-item__icon--ok" aria-hidden="true">
                  <i class="ri-mail-check-line" />
                </div>
                <div class="sec-item__main">
                  <div class="sec-item__title-row">
                    <span class="sec-item__title">邮箱登录</span>
                    <span
                      class="sec-tag"
                      :class="security.emailBound ? 'sec-tag--ok' : 'sec-tag--warn'"
                    >
                      {{ security.emailBound ? '已绑定' : '未绑定' }}
                    </span>
                  </div>
                  <p class="sec-item__desc">
                    {{
                      security.emailBound
                        ? `当前绑定：${security.emailMasked || '***'}`
                        : '尚未绑定可用于登录与找回密码的邮箱'
                    }}
                  </p>
                </div>
              </li>

              <li class="sec-item">
                <div
                  class="sec-item__icon"
                  :class="security.passwordSet ? 'sec-item__icon--ok' : 'sec-item__icon--warn'"
                  aria-hidden="true"
                >
                  <i class="ri-lock-password-line" />
                </div>
                <div class="sec-item__main">
                  <div class="sec-item__title-row">
                    <span class="sec-item__title">登录密码</span>
                    <span
                      class="sec-tag"
                      :class="security.passwordSet ? 'sec-tag--ok' : 'sec-tag--warn'"
                    >
                      {{ security.passwordSet ? '已设置' : '未设置' }}
                    </span>
                  </div>
                  <p class="sec-item__desc">
                    <template v-if="security.passwordSet && security.lastPasswordChangeTime">
                      上次修改 {{ formatDateTime(security.lastPasswordChangeTime) }}
                      <span v-if="passwordAgeHint" class="sec-item__aside">· {{ passwordAgeHint }}</span>
                    </template>
                    <template v-else-if="security.passwordSet">
                      已设置密码，建议定期更换以降低风险
                    </template>
                    <template v-else>
                      未设置密码，请通过「忘记密码」流程完成初始化
                    </template>
                  </p>
                </div>
                <div class="sec-item__action">
                  <button
                    v-if="security.passwordSet"
                    type="button"
                    class="sec-action-btn"
                    :class="{ 'sec-action-btn--muted': passwordPanelOpen }"
                    @click="togglePasswordPanel"
                  >
                    {{ passwordPanelOpen ? '收起' : '修改密码' }}
                  </button>
                  <button
                    v-else
                    type="button"
                    class="sec-action-btn sec-action-btn--muted"
                    @click="goForgotPassword"
                  >
                    去设置
                  </button>
                </div>
              </li>
            </ul>

            <Transition name="sec-slide">
              <div v-if="passwordPanelOpen && security.passwordSet" class="sec-password">
                <div class="sec-password__tip">
                  <i class="ri-information-line" aria-hidden="true" />
                  <span>更新成功后将退出全部设备会话，需使用新密码重新登录。</span>
                </div>
                <el-form
                  ref="passwordFormRef"
                  :model="passwordForm"
                  :rules="passwordRules"
                  label-position="top"
                  class="settings-form"
                  @submit.prevent
                >
                  <el-form-item label="当前密码" prop="oldPassword">
                    <el-input
                      v-model="passwordForm.oldPassword"
                      type="password"
                      show-password
                      autocomplete="current-password"
                      placeholder="请输入当前密码"
                    />
                  </el-form-item>
                  <el-form-item label="新密码" prop="newPassword">
                    <el-input
                      v-model="passwordForm.newPassword"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      placeholder="8～72 位，需同时包含字母和数字"
                    />
                    <div v-if="passwordForm.newPassword" class="pwd-meter">
                      <div class="pwd-meter__track">
                        <span
                          class="pwd-meter__fill"
                          :class="'pwd-meter__fill--' + passwordStrength.level"
                          :style="{ width: passwordStrength.percent + '%' }"
                        />
                      </div>
                      <span class="pwd-meter__label" :class="'pwd-meter__label--' + passwordStrength.level">
                        强度：{{ passwordStrength.label }}
                      </span>
                    </div>
                  </el-form-item>
                  <el-form-item label="确认新密码" prop="confirmPassword">
                    <el-input
                      v-model="passwordForm.confirmPassword"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      placeholder="再次输入新密码"
                      @keyup.enter="submitPassword"
                    />
                  </el-form-item>
                  <div class="form-actions form-actions--split">
                    <el-button @click="closePasswordPanel">取消</el-button>
                    <el-button
                      type="primary"
                      :loading="passwordSaving"
                      :disabled="passwordStrength.level === 'weak'"
                      @click="submitPassword"
                    >
                      确认更新
                    </el-button>
                  </div>
                </el-form>
              </div>
            </Transition>
          </div>

          <div class="sec-section">
            <h3 class="sec-section__title">登录会话</h3>
            <ul class="sec-list">
              <li class="sec-item">
                <div
                  class="sec-item__icon"
                  :class="security.hasOtherSessions ? 'sec-item__icon--warn' : 'sec-item__icon--ok'"
                  aria-hidden="true"
                >
                  <i class="ri-devices-line" />
                </div>
                <div class="sec-item__main">
                  <div class="sec-item__title-row">
                    <span class="sec-item__title">设备会话</span>
                    <span
                      class="sec-tag"
                      :class="security.hasOtherSessions ? 'sec-tag--warn' : 'sec-tag--ok'"
                    >
                      {{ security.hasOtherSessions ? '多设备在线' : '仅本机' }}
                    </span>
                  </div>
                  <p class="sec-item__desc">
                    {{
                      security.hasOtherSessions
                        ? '检测到其他设备仍持有有效登录。若非本人操作，请立即退出全部设备并修改密码。'
                        : '当前仅本机存在有效登录会话。'
                    }}
                  </p>
                </div>
              </li>
            </ul>
          </div>

          <div class="sec-danger">
            <div class="sec-danger__head">
              <i class="ri-alarm-warning-line" aria-hidden="true" />
              <div>
                <h3 class="sec-danger__title">紧急操作</h3>
                <p class="sec-danger__desc">
                  退出全部设备会使所有端（含本机）的登录立即失效，用于怀疑账号被盗时快速止损。
                </p>
              </div>
            </div>
            <el-button type="danger" :loading="revoking" @click="confirmRevokeAll">
              退出所有设备
            </el-button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  getAccountSettings,
  getAccountSecurity,
  updateUserProfile,
  uploadUserAvatar,
  changeAccountPassword,
  revokeAllSessions,
} from '@/api/user'

function scorePassword(password) {
  const raw = password != null ? String(password) : ''
  if (!raw) {
    return { level: 'weak', label: '弱', percent: 0 }
  }
  let score = 0
  if (raw.length >= 8) score += 1
  if (raw.length >= 12) score += 1
  if (/[a-z]/.test(raw) && /[A-Z]/.test(raw)) score += 1
  if (/\d/.test(raw)) score += 1
  if (/[^A-Za-z0-9]/.test(raw)) score += 1
  const hasLetter = /[A-Za-z]/.test(raw)
  const hasDigit = /\d/.test(raw)
  if (!hasLetter || !hasDigit || raw.length < 8) {
    return { level: 'weak', label: '弱', percent: 28 }
  }
  if (score <= 2) return { level: 'fair', label: '中', percent: 55 }
  if (score === 3) return { level: 'good', label: '较强', percent: 78 }
  return { level: 'strong', label: '强', percent: 100 }
}

export default {
  name: 'AccountSettings',
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
      if (raw === this.passwordForm.oldPassword) {
        callback(new Error('新密码不能与当前密码相同'))
        return
      }
      callback()
    }
    const validateConfirm = (_rule, value, callback) => {
      if (value !== this.passwordForm.newPassword) {
        callback(new Error('两次输入的新密码不一致'))
      } else {
        callback()
      }
    }
    return {
      pageLoading: false,
      securityRefreshing: false,
      activeTab: 'profile',
      passwordPanelOpen: false,
      avatarUploading: false,
      profileSaving: false,
      passwordSaving: false,
      revoking: false,
      security: {
        emailMasked: '',
        emailBound: false,
        passwordSet: false,
        lastPasswordChangeTime: '',
        hasOtherSessions: false,
      },
      profileForm: {
        nickname: '',
        avatar: '',
        signature: '',
        gender: 0,
        birthday: '',
      },
      passwordForm: {
        oldPassword: '',
        newPassword: '',
        confirmPassword: '',
      },
      profileRules: {
        nickname: [
          { required: true, message: '请输入昵称', trigger: 'blur' },
          { max: 50, message: '昵称最多 50 个字符', trigger: 'blur' },
        ],
        signature: [{ max: 255, message: '签名最多 255 个字符', trigger: 'blur' }],
      },
      passwordRules: {
        oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
        newPassword: [{ required: true, validator: validateNewPassword, trigger: 'blur' }],
        confirmPassword: [
          { required: true, message: '请再次输入新密码', trigger: 'blur' },
          { validator: validateConfirm, trigger: 'blur' },
        ],
      },
    }
  },
  computed: {
    authToken() {
      return useAuthStore().token
    },
    avatarFallback() {
      const name = this.profileForm.nickname || 'U'
      return String(name).charAt(0).toUpperCase()
    },
    passwordStrength() {
      return scorePassword(this.passwordForm.newPassword)
    },
    securityScore() {
      let score = 20
      if (this.security.emailBound) score += 30
      if (this.security.passwordSet) score += 35
      if (this.security.passwordSet && this.security.lastPasswordChangeTime) {
        const ageDays = this.passwordAgeDays
        if (ageDays != null && ageDays <= 180) score += 10
      }
      if (!this.security.hasOtherSessions) score += 5
      return Math.max(0, Math.min(100, score))
    },
    securityLevel() {
      const score = this.securityScore
      if (!this.security.passwordSet || !this.security.emailBound) {
        return {
          key: 'risk',
          label: '需加强',
          icon: 'ri-shield-flash-line',
          hint: '关键登录方式不完整，请优先完善邮箱与密码。',
        }
      }
      if (this.security.hasOtherSessions) {
        return {
          key: 'warn',
          label: '注意',
          icon: 'ri-shield-line',
          hint: '存在其他设备会话。若非本人登录，请立即退出全部设备。',
        }
      }
      if (score >= 85) {
        return {
          key: 'ok',
          label: '良好',
          icon: 'ri-shield-check-line',
          hint: '基础安全项已就绪。建议定期更换密码，保持良好习惯。',
        }
      }
      return {
        key: 'warn',
        label: '一般',
        icon: 'ri-shield-line',
        hint: '可继续加固：更新密码、确认设备会话是否均为本人操作。',
      }
    },
    passwordAgeDays() {
      const raw = this.security.lastPasswordChangeTime
      if (!raw) return null
      const ts = Date.parse(String(raw).replace(' ', 'T'))
      if (!Number.isFinite(ts)) return null
      return Math.floor((Date.now() - ts) / (24 * 60 * 60 * 1000))
    },
    passwordAgeHint() {
      const days = this.passwordAgeDays
      if (days == null) return ''
      if (days <= 0) return '今天刚更新'
      if (days < 30) return `${days} 天前`
      if (days < 180) return '状态正常'
      return '建议尽快更换'
    },
  },
  watch: {
    '$route.query.tab': {
      immediate: true,
      handler(tab) {
        const value = tab != null ? String(tab).trim() : ''
        if (value === 'security') this.activeTab = 'security'
        else if (value === 'profile') this.activeTab = 'profile'
      },
    },
  },
  mounted() {
    this.loadSettings()
  },
  methods: {
    async loadSettings() {
      if (!this.authToken) {
        this.$router.replace({
          path: '/login',
          query: { redirect: this.$route.fullPath },
        })
        return
      }
      this.pageLoading = true
      try {
        const data = await getAccountSettings(this.authToken)
        this.profileForm = {
          nickname: data.profile.nickname || '',
          avatar: data.profile.avatar || '',
          signature: data.profile.signature || '',
          gender: data.profile.gender != null ? Number(data.profile.gender) : 0,
          birthday: data.profile.birthday || '',
        }
        this.security = { ...data.security }
      } catch (error) {
        ElMessage.error(error?.message || '加载账号设置失败')
      } finally {
        this.pageLoading = false
      }
    },
    switchTab(tab) {
      if (tab !== 'profile' && tab !== 'security') return
      this.activeTab = tab
      if (tab === 'profile') this.closePasswordPanel()
      const query = { ...this.$route.query, tab }
      this.$router.replace({ query }).catch(() => {})
    },
    async refreshSecurity() {
      if (!this.authToken || this.securityRefreshing) return
      this.securityRefreshing = true
      try {
        this.security = await getAccountSecurity(this.authToken)
        ElMessage.success('安全状态已更新')
      } catch (error) {
        ElMessage.error(error?.message || '刷新失败')
      } finally {
        this.securityRefreshing = false
      }
    },
    togglePasswordPanel() {
      if (this.passwordPanelOpen) {
        this.closePasswordPanel()
      } else {
        this.passwordPanelOpen = true
      }
    },
    closePasswordPanel() {
      this.passwordPanelOpen = false
      this.passwordForm = { oldPassword: '', newPassword: '', confirmPassword: '' }
      this.$nextTick(() => {
        this.$refs.passwordFormRef?.clearValidate?.()
      })
    },
    goForgotPassword() {
      this.$router.push({ path: '/forgot-password' })
    },
    beforeAvatarUpload(file) {
      const okType = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
      if (!okType) {
        ElMessage.warning('仅支持 jpg / png / webp')
        return false
      }
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning('头像大小不能超过 5MB')
        return false
      }
      return true
    },
    async handleAvatarUpload({ file }) {
      this.avatarUploading = true
      try {
        const res = await uploadUserAvatar(file, this.authToken)
        this.profileForm.avatar = res.url
        ElMessage.success('头像已上传，记得保存资料')
      } catch (error) {
        ElMessage.error(error?.message || '头像上传失败')
      } finally {
        this.avatarUploading = false
      }
    },
    saveProfile() {
      this.$refs.profileFormRef.validate(async (valid) => {
        if (!valid || this.profileSaving) return
        this.profileSaving = true
        try {
          const payload = {
            nickname: this.profileForm.nickname,
            signature: this.profileForm.signature,
            gender: this.profileForm.gender,
            birthday: this.profileForm.birthday || '',
            avatar: this.profileForm.avatar || '',
          }
          const res = await updateUserProfile(payload, this.authToken)
          if (res.data) {
            this.profileForm.nickname = res.data.nickname || this.profileForm.nickname
            this.profileForm.avatar = res.data.avatar || this.profileForm.avatar
            this.profileForm.signature =
              res.data.signature != null ? res.data.signature : this.profileForm.signature
            this.profileForm.gender =
              res.data.gender != null ? Number(res.data.gender) : this.profileForm.gender
            this.profileForm.birthday =
              res.data.birthday != null ? res.data.birthday : this.profileForm.birthday
          }
          useAuthStore().updateCurrentUser({
            nickname: this.profileForm.nickname,
            signature: this.profileForm.signature,
            avatar: this.profileForm.avatar,
          })
          ElMessage.success('资料已保存')
        } catch (error) {
          ElMessage.error(error?.message || '保存失败')
        } finally {
          this.profileSaving = false
        }
      })
    },
    submitPassword() {
      this.$refs.passwordFormRef.validate(async (valid) => {
        if (!valid || this.passwordSaving) return
        if (this.passwordStrength.level === 'weak') {
          ElMessage.warning('请设置更安全的新密码')
          return
        }
        try {
          await ElMessageBox.confirm(
            '修改密码后，所有设备（含本机）将立即下线，需使用新密码重新登录。',
            '确认修改密码',
            {
              type: 'warning',
              confirmButtonText: '确认修改',
              cancelButtonText: '再想想',
              customClass: 'meme-confirm-box',
            }
          )
        } catch {
          return
        }
        this.passwordSaving = true
        try {
          const res = await changeAccountPassword({ ...this.passwordForm }, this.authToken)
          ElMessage.success(res.message || '密码已更新，请重新登录')
          useAuthStore().clearAuthState()
          this.$router.replace({ path: '/login', query: { redirect: '/settings?tab=security' } })
        } catch (error) {
          ElMessage.error(error?.message || '修改密码失败')
        } finally {
          this.passwordSaving = false
        }
      })
    },
    async confirmRevokeAll() {
      try {
        await ElMessageBox.confirm(
          '将使全部设备（含当前）的登录立即失效，并需重新登录。仅在怀疑账号异常时操作。',
          '退出所有设备',
          {
            type: 'warning',
            confirmButtonText: '立即全部退出',
            cancelButtonText: '取消',
            confirmButtonClass: 'el-button--danger',
            customClass: 'meme-confirm-box',
          }
        )
      } catch {
        return
      }
      this.revoking = true
      try {
        const res = await revokeAllSessions(this.authToken)
        ElMessage.success(`已退出 ${res.revokedCount} 个会话，请重新登录`)
        useAuthStore().clearAuthState()
        this.$router.replace({ path: '/login', query: { redirect: '/settings?tab=security' } })
      } catch (error) {
        ElMessage.error(error?.message || '操作失败')
      } finally {
        this.revoking = false
      }
    },
    formatDateTime(raw) {
      const text = raw != null ? String(raw).trim() : ''
      if (!text) return ''
      return text.replace('T', ' ').slice(0, 19)
    },
  },
}
</script>

<style scoped>
.settings-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 20px 64px;
}

.settings-hero {
  margin-bottom: 24px;
}

.settings-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.settings-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.settings-body {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  min-height: 320px;
}

.settings-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: var(--meme-radius-lg);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
}

.settings-nav__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.settings-nav__item:hover {
  color: var(--meme-text);
  background: var(--meme-bg-muted);
}

.settings-nav__item.is-active {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.settings-panel {
  padding: 24px 28px 28px;
  border-radius: var(--meme-radius-lg);
  border: 1px solid var(--meme-border);
  background: var(--meme-gradient-dialog), var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.settings-panel__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
  color: var(--meme-text);
}

.settings-panel__desc {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.settings-subtitle {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: var(--meme-text);
}

.settings-form {
  max-width: 480px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.avatar-preview {
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-weight: 700;
}

.avatar-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.form-actions {
  margin-top: 8px;
}

.form-actions--split {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* —— 账号安全 —— */
.sec-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}

.sec-head .settings-panel__desc {
  margin-bottom: 18px;
}

.sec-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--meme-border);
  border-radius: 10px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.sec-refresh:hover:not(:disabled) {
  color: var(--meme-primary);
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.sec-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.sec-refresh .is-spinning {
  display: inline-block;
  animation: sec-spin 0.8s linear infinite;
}

.sec-health {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 16px 18px;
  margin-bottom: 24px;
  border-radius: 14px;
  border: 1px solid var(--meme-border);
}

.sec-health--ok {
  background: var(--meme-success-soft);
  border-color: color-mix(in srgb, var(--meme-success) 28%, var(--meme-border));
}

.sec-health--warn {
  background: var(--meme-warning-soft);
  border-color: color-mix(in srgb, var(--meme-warning) 28%, var(--meme-border));
}

.sec-health--risk {
  background: var(--meme-danger-soft);
  border-color: color-mix(in srgb, var(--meme-danger) 28%, var(--meme-border));
}

.sec-health__icon {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 22px;
  background: var(--meme-bg-card);
  color: var(--meme-text-secondary);
}

.sec-health--ok .sec-health__icon {
  color: var(--meme-success);
}

.sec-health--warn .sec-health__icon {
  color: var(--meme-warning);
}

.sec-health--risk .sec-health__icon {
  color: var(--meme-danger);
}

.sec-health__body {
  min-width: 0;
  flex: 1;
}

.sec-health__top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.sec-health__label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--meme-text-muted);
  text-transform: uppercase;
}

.sec-health__badge {
  font-size: 13px;
  font-weight: 700;
  color: var(--meme-text);
}

.sec-health__text {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
}

.sec-health__meter {
  height: 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--meme-text) 8%, transparent);
  overflow: hidden;
}

.sec-health__meter-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--meme-primary);
  transition: width 0.35s ease;
}

.sec-health--ok .sec-health__meter-bar {
  background: var(--meme-success);
}

.sec-health--warn .sec-health__meter-bar {
  background: var(--meme-warning);
}

.sec-health--risk .sec-health__meter-bar {
  background: var(--meme-danger);
}

.sec-section {
  margin-bottom: 22px;
}

.sec-section__title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--meme-text-muted);
  text-transform: uppercase;
}

.sec-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--meme-border);
  border-radius: 14px;
  overflow: hidden;
  background: var(--meme-bg-card);
}

.sec-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px 16px;
}

.sec-item + .sec-item {
  border-top: 1px solid var(--meme-border);
}

.sec-item__icon {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  font-size: 20px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
}

.sec-item__icon--ok {
  background: var(--meme-success-soft);
  color: var(--meme-success);
}

.sec-item__icon--warn {
  background: var(--meme-warning-soft);
  color: var(--meme-warning);
}

.sec-item__main {
  min-width: 0;
}

.sec-item__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.sec-item__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--meme-text);
}

.sec-item__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--meme-text-secondary);
}

.sec-item__aside {
  color: var(--meme-text-muted);
}

.sec-item__action {
  flex-shrink: 0;
}

.sec-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--meme-border-accent);
  border-radius: 10px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.sec-action-btn:hover {
  border-color: var(--meme-primary);
  color: var(--meme-primary-dark);
  filter: brightness(1.06);
}

.sec-action-btn--muted {
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
  color: var(--meme-text-secondary);
}

.sec-action-btn--muted:hover {
  color: var(--meme-text);
  border-color: var(--meme-border-strong);
  filter: none;
}

.sec-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.sec-tag--ok {
  color: var(--meme-success);
  background: var(--meme-success-soft);
}

.sec-tag--warn {
  color: var(--meme-warning);
  background: var(--meme-warning-soft);
}

.sec-password {
  margin-top: 12px;
  padding: 16px 16px 8px;
  border-radius: 14px;
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-gradient-card);
}

.sec-password__tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--meme-primary-soft);
  color: var(--meme-accent-text);
  font-size: 12px;
  line-height: 1.5;
}

.sec-password__tip i {
  flex-shrink: 0;
  margin-top: 1px;
  font-size: 15px;
}

.pwd-meter {
  margin-top: 8px;
}

.pwd-meter__track {
  height: 5px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
  overflow: hidden;
}

.pwd-meter__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.25s ease, background 0.25s ease;
}

.pwd-meter__fill--weak {
  background: var(--meme-danger);
}

.pwd-meter__fill--fair {
  background: var(--meme-warning);
}

.pwd-meter__fill--good,
.pwd-meter__fill--strong {
  background: var(--meme-success);
}

.pwd-meter__label {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.pwd-meter__label--weak {
  color: var(--meme-danger);
}

.pwd-meter__label--fair {
  color: var(--meme-warning);
}

.pwd-meter__label--good,
.pwd-meter__label--strong {
  color: var(--meme-success);
}

.sec-danger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--meme-danger) 28%, var(--meme-border));
  background: var(--meme-danger-soft);
}

.sec-danger__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}

.sec-danger__head > i {
  flex-shrink: 0;
  font-size: 22px;
  color: var(--meme-danger);
  margin-top: 2px;
}

.sec-danger__title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 700;
  color: var(--meme-text);
}

.sec-danger__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
}

.sec-slide-enter-active,
.sec-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.sec-slide-enter-from,
.sec-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@keyframes sec-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .settings-body {
    grid-template-columns: 1fr;
  }

  .settings-nav {
    flex-direction: row;
  }

  .settings-nav__item {
    flex: 1;
    justify-content: center;
  }

  .settings-panel {
    padding: 20px 16px 24px;
  }

  .sec-item {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .sec-item__action {
    grid-column: 2;
  }

  .sec-danger {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
