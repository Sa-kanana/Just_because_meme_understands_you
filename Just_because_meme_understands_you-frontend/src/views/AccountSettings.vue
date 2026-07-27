<template>
  <div class="settings-page">
    <header class="settings-hero">
      <p class="settings-kicker">ACCOUNT</p>
      <h1 class="settings-title">账号设置</h1>
      <p class="settings-lead">管理个人资料与账号安全，保护你的梗位。</p>
    </header>

    <div class="settings-body-wrap">
      <div v-if="pageLoading" class="settings-page-loading">
        <span class="settings-page-loading__spinner" />
        <span>加载中...</span>
      </div>

      <div class="settings-body" :class="{ 'is-busy': pageLoading }">
        <aside class="settings-nav" aria-label="设置分区">
          <vs-card class="settings-nav-card">
            <template #text>
              <div class="settings-nav__list">
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
              </div>
            </template>
          </vs-card>
        </aside>

        <section class="settings-panel-wrap">
          <vs-card class="settings-panel-card">
            <template #text>
              <div class="settings-panel">
                <template v-if="activeTab === 'profile'">
                  <div class="settings-panel__head">
                    <span class="settings-panel__mark" aria-hidden="true">
                      <i class="ri-user-3-fill" />
                    </span>
                    <div>
                      <h2 class="settings-panel__title">个人资料</h2>
                      <p class="settings-panel__desc">这些信息会展示在个人主页与发布的梗中。</p>
                    </div>
                  </div>

                  <ui-form
                    ref="profileFormRef"
                    :model="profileForm"
                    :rules="profileRules"
                    label-position="top"
                    class="settings-form"
                  >
                    <div class="avatar-block">
                      <div
                        class="avatar-preview-wrap"
                        :class="{ 'is-previewable': !!profileForm.avatar }"
                        :role="profileForm.avatar ? 'button' : undefined"
                        :tabindex="profileForm.avatar ? 0 : undefined"
                        :aria-label="profileForm.avatar ? '点击查看头像' : undefined"
                        @click="openAvatarPreview"
                        @keydown.enter.prevent="openAvatarPreview"
                      >
                        <ui-avatar
                          :size="88"
                          :src="profileForm.avatar"
                          :fallback="profileForm.nickname || 'U'"
                          class="avatar-preview"
                        />
                        <span
                          v-if="profileForm.avatar"
                          class="avatar-zoom-hint"
                          aria-hidden="true"
                        >
                          <i class="ri-zoom-in-line" />
                        </span>
                      </div>
                      <div class="avatar-actions">
                        <p class="avatar-actions__title">头像</p>
                        <p class="avatar-hint">支持 jpg / png / webp，建议正方形；点击头像可放大查看</p>
                        <ui-upload
                          accept="image/jpeg,image/png,image/webp"
                          :http-request="handleAvatarUpload"
                        >
                          <button
                            type="button"
                            class="settings-btn settings-btn--soft"
                            :disabled="avatarUploading"
                          >
                            <i
                              :class="avatarUploading ? 'ri-loader-4-line is-spinning' : 'ri-image-edit-line'"
                              aria-hidden="true"
                            />
                            {{ avatarUploading ? '上传中...' : '更换头像' }}
                          </button>
                        </ui-upload>
                      </div>
                    </div>

                    <ui-form-item label="昵称" prop="nickname">
                      <vs-input
                        v-model="profileForm.nickname"
                        maxlength="50"
                        clearable
                        block
                        placeholder="怎么称呼你"
                        class="settings-input"
                      />
                    </ui-form-item>

                    <ui-form-item label="个性签名" prop="signature">
                      <vs-input
                        v-model="profileForm.signature"
                        type="textarea"
                        :rows="3"
                        maxlength="255"
                        block
                        placeholder="一句话介绍自己"
                        class="settings-input settings-input--area"
                      />
                    </ui-form-item>

                    <ui-form-item label="性别" prop="gender">
                      <div class="gender-segment" role="radiogroup" aria-label="性别">
                        <button
                          v-for="opt in genderOptions"
                          :key="`gender-${opt.value}`"
                          type="button"
                          class="gender-segment__item"
                          :class="{ 'is-active': profileForm.gender === opt.value }"
                          role="radio"
                          :aria-checked="profileForm.gender === opt.value"
                          @click="profileForm.gender = opt.value"
                        >
                          <i :class="opt.icon" aria-hidden="true" />
                          {{ opt.label }}
                        </button>
                      </div>
                    </ui-form-item>

                    <ui-form-item label="生日" prop="birthday">
                      <ui-date-picker
                        v-model="profileForm.birthday"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="选择生日"
                        class="settings-date"
                      />
                    </ui-form-item>

                    <div class="form-actions">
                      <button
                        type="button"
                        class="settings-btn settings-btn--primary"
                        :disabled="profileSaving"
                        @click="saveProfile"
                      >
                        <i
                          :class="profileSaving ? 'ri-loader-4-line is-spinning' : 'ri-check-line'"
                          aria-hidden="true"
                        />
                        {{ profileSaving ? '保存中...' : '保存资料' }}
                      </button>
                    </div>
                  </ui-form>
                </template>

                <template v-else>
                  <div class="sec-head">
                    <div class="settings-panel__head settings-panel__head--inline">
                      <span class="settings-panel__mark settings-panel__mark--shield" aria-hidden="true">
                        <i class="ri-shield-keyhole-fill" />
                      </span>
                      <div>
                        <h2 class="settings-panel__title">账号安全</h2>
                        <p class="settings-panel__desc">
                          保护登录凭证与设备会话。敏感操作完成后需重新登录。
                        </p>
                      </div>
                    </div>
                    <button
                      type="button"
                      class="sec-refresh"
                      :disabled="securityRefreshing"
                      :title="securityRefreshing ? '刷新中' : '刷新安全状态'"
                      @click="refreshSecurity"
                    >
                      <i
                        class="ri-refresh-line"
                        :class="{ 'is-spinning': securityRefreshing }"
                        aria-hidden="true"
                      />
                      刷新
                    </button>
                  </div>

                  <vs-card class="sec-health-card" :class="'is-' + securityLevel.key">
                    <template #text>
                      <div class="sec-health">
                        <div class="sec-health__icon" aria-hidden="true">
                          <i :class="securityLevel.icon" />
                        </div>
                        <div class="sec-health__body">
                          <div class="sec-health__top">
                            <span class="sec-health__label">安全评级</span>
                            <ui-tag :type="securityLevelTagType" class="sec-health__badge">
                              {{ securityLevel.label }}
                            </ui-tag>
                          </div>
                          <p class="sec-health__text">{{ securityLevel.hint }}</p>
                          <ui-progress
                            :percentage="securityScore"
                            class="sec-health__progress"
                            :class="'is-' + securityLevel.key"
                          />
                        </div>
                      </div>
                    </template>
                  </vs-card>

                  <div class="sec-section">
                    <h3 class="sec-section__title">
                      <i class="ri-key-2-line" aria-hidden="true" />
                      登录方式
                    </h3>
                    <vs-card class="sec-list-card">
                      <template #text>
                        <ul class="sec-list">
                          <li class="sec-item">
                            <div class="sec-item__icon sec-item__icon--ok" aria-hidden="true">
                              <i class="ri-mail-check-line" />
                            </div>
                            <div class="sec-item__main">
                              <div class="sec-item__title-row">
                                <span class="sec-item__title">邮箱登录</span>
                                <ui-tag :type="security.emailBound ? 'success' : 'warning'">
                                  {{ security.emailBound ? '已绑定' : '未绑定' }}
                                </ui-tag>
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
                                <ui-tag :type="security.passwordSet ? 'success' : 'warning'">
                                  {{ security.passwordSet ? '已设置' : '未设置' }}
                                </ui-tag>
                              </div>
                              <p class="sec-item__desc">
                                <template v-if="security.passwordSet && security.lastPasswordChangeTime">
                                  上次修改 {{ formatDateTime(security.lastPasswordChangeTime) }}
                                  <span v-if="passwordAgeHint" class="sec-item__aside">
                                    · {{ passwordAgeHint }}
                                  </span>
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
                                <i
                                  :class="passwordPanelOpen ? 'ri-arrow-up-s-line' : 'ri-edit-line'"
                                  aria-hidden="true"
                                />
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
                      </template>
                    </vs-card>

                    <Transition name="sec-slide">
                      <vs-card v-if="passwordPanelOpen && security.passwordSet" class="sec-password-card">
                        <template #text>
                          <div class="sec-password">
                            <vs-alert color="primary" class="sec-password__alert">
                              更新成功后将退出全部设备会话，需使用新密码重新登录。
                            </vs-alert>
                            <ui-form
                              ref="passwordFormRef"
                              :model="passwordForm"
                              :rules="passwordRules"
                              label-position="top"
                              class="settings-form"
                              @submit.prevent
                            >
                              <ui-form-item label="当前密码" prop="oldPassword">
                                <vs-input
                                  v-model="passwordForm.oldPassword"
                                  type="password"
                                  show-password
                                  autocomplete="current-password"
                                  placeholder="请输入当前密码"
                                  block
                                  class="settings-input"
                                />
                              </ui-form-item>
                              <ui-form-item label="新密码" prop="newPassword">
                                <vs-input
                                  v-model="passwordForm.newPassword"
                                  type="password"
                                  show-password
                                  autocomplete="new-password"
                                  placeholder="8～72 位，需同时包含字母和数字"
                                  block
                                  class="settings-input"
                                />
                                <div v-if="passwordForm.newPassword" class="pwd-meter">
                                  <div class="pwd-meter__track">
                                    <span
                                      class="pwd-meter__fill"
                                      :class="'pwd-meter__fill--' + passwordStrength.level"
                                      :style="{ width: passwordStrength.percent + '%' }"
                                    />
                                  </div>
                                  <span
                                    class="pwd-meter__label"
                                    :class="'pwd-meter__label--' + passwordStrength.level"
                                  >
                                    强度：{{ passwordStrength.label }}
                                  </span>
                                </div>
                              </ui-form-item>
                              <ui-form-item label="确认新密码" prop="confirmPassword">
                                <vs-input
                                  v-model="passwordForm.confirmPassword"
                                  type="password"
                                  show-password
                                  autocomplete="new-password"
                                  placeholder="再次输入新密码"
                                  block
                                  class="settings-input"
                                  @keydown.enter="submitPassword"
                                />
                              </ui-form-item>
                              <div class="form-actions form-actions--split">
                                <button
                                  type="button"
                                  class="settings-btn settings-btn--ghost"
                                  @click="closePasswordPanel"
                                >
                                  取消
                                </button>
                                <button
                                  type="button"
                                  class="settings-btn settings-btn--primary"
                                  :disabled="passwordSaving || passwordStrength.level === 'weak'"
                                  @click="submitPassword"
                                >
                                  <i
                                    :class="
                                      passwordSaving
                                        ? 'ri-loader-4-line is-spinning'
                                        : 'ri-lock-password-line'
                                    "
                                    aria-hidden="true"
                                  />
                                  {{ passwordSaving ? '更新中...' : '确认更新' }}
                                </button>
                              </div>
                            </ui-form>
                          </div>
                        </template>
                      </vs-card>
                    </Transition>
                  </div>

                  <div class="sec-section">
                    <h3 class="sec-section__title">
                      <i class="ri-computer-line" aria-hidden="true" />
                      登录会话
                    </h3>
                    <vs-card class="sec-list-card">
                      <template #text>
                        <ul class="sec-list">
                          <li class="sec-item">
                            <div
                              class="sec-item__icon"
                              :class="
                                security.hasOtherSessions
                                  ? 'sec-item__icon--warn'
                                  : 'sec-item__icon--ok'
                              "
                              aria-hidden="true"
                            >
                              <i class="ri-smartphone-line" />
                            </div>
                            <div class="sec-item__main">
                              <div class="sec-item__title-row">
                                <span class="sec-item__title">设备会话</span>
                                <ui-tag :type="security.hasOtherSessions ? 'warning' : 'success'">
                                  {{ security.hasOtherSessions ? '多设备在线' : '仅本机' }}
                                </ui-tag>
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
                      </template>
                    </vs-card>
                  </div>

                  <vs-card class="sec-danger-card">
                    <template #text>
                      <div class="sec-danger">
                        <div class="sec-danger__head">
                          <span class="sec-danger__icon" aria-hidden="true">
                            <i class="ri-alarm-warning-fill" />
                          </span>
                          <div>
                            <h3 class="sec-danger__title">紧急操作</h3>
                            <p class="sec-danger__desc">
                              退出全部设备会使所有端（含本机）的登录立即失效，用于怀疑账号被盗时快速止损。
                            </p>
                          </div>
                        </div>
                        <button
                          type="button"
                          class="settings-btn settings-btn--danger"
                          :disabled="revoking"
                          @click="confirmRevokeAll"
                        >
                          <i
                            :class="revoking ? 'ri-loader-4-line is-spinning' : 'ri-logout-box-r-line'"
                            aria-hidden="true"
                          />
                          {{ revoking ? '处理中...' : '退出所有设备' }}
                        </button>
                      </div>
                    </template>
                  </vs-card>
                </template>
              </div>
            </template>
          </vs-card>
        </section>
      </div>
    </div>

    <vs-dialog
      v-model="avatarPreviewVisible"
      full-screen
      not-padding
      class="settings-avatar-viewer"
    >
      <div class="settings-avatar-viewer__body" @click="closeAvatarPreview">
        <button
          type="button"
          class="settings-avatar-viewer__close"
          aria-label="关闭预览"
          @click.stop="closeAvatarPreview"
        >
          <i class="ri-close-line" />
        </button>
        <ui-image
          v-if="avatarPreviewSrc"
          :src="avatarPreviewSrc"
          fit="contain"
          class="settings-avatar-viewer__img"
          @click.stop
        />
        <p class="settings-avatar-viewer__tip">点击空白处关闭</p>
      </div>
    </vs-dialog>
  </div>
</template>

<script>
import { toast, confirmBox } from '@/utils/uiFeedback'
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
      avatarPreviewVisible: false,
      avatarPreviewSrc: '',
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
      genderOptions: [
        { value: 0, label: '保密', icon: 'ri-eye-off-line' },
        { value: 1, label: '男', icon: 'ri-men-line' },
        { value: 2, label: '女', icon: 'ri-women-line' },
      ],
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
    securityLevelTagType() {
      const key = this.securityLevel && this.securityLevel.key
      if (key === 'ok') return 'success'
      if (key === 'risk') return 'danger'
      return 'warning'
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
        toast.error(error?.message || '加载账号设置失败')
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
        toast.success('安全状态已更新')
      } catch (error) {
        toast.error(error?.message || '刷新失败')
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
    openAvatarPreview() {
      const src = String(this.profileForm.avatar || '').trim()
      if (!src) return
      this.avatarPreviewSrc = src
      this.avatarPreviewVisible = true
    },
    closeAvatarPreview() {
      this.avatarPreviewVisible = false
      this.avatarPreviewSrc = ''
    },
    async handleAvatarUpload({ file }) {
      const okType = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
      if (!okType) {
        toast.warning('仅支持 jpg / png / webp')
        return
      }
      if (file.size > 5 * 1024 * 1024) {
        toast.warning('头像大小不能超过 5MB')
        return
      }
      this.avatarUploading = true
      try {
        const res = await uploadUserAvatar(file, this.authToken)
        this.profileForm.avatar = res.url
        toast.success('头像已上传，记得保存资料')
      } catch (error) {
        toast.error(error?.message || '头像上传失败')
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
          toast.success('资料已保存')
        } catch (error) {
          toast.error(error?.message || '保存失败')
        } finally {
          this.profileSaving = false
        }
      })
    },
    submitPassword() {
      this.$refs.passwordFormRef.validate(async (valid) => {
        if (!valid || this.passwordSaving) return
        if (this.passwordStrength.level === 'weak') {
          toast.warning('请设置更安全的新密码')
          return
        }
        try {
          await confirmBox(
            '修改密码后，所有设备（含本机）将立即下线，需使用新密码重新登录。',
            '确认修改密码',
            {
              type: 'warning',
              confirmButtonText: '确认修改',
              cancelButtonText: '再想想',
            }
          )
        } catch (e) {
          if (e === 'cancel') return
          return
        }
        this.passwordSaving = true
        try {
          const res = await changeAccountPassword({ ...this.passwordForm }, this.authToken)
          toast.success(res.message || '密码已更新，请重新登录')
          useAuthStore().clearAuthState()
          this.$router.replace({ path: '/login', query: { redirect: '/settings?tab=security' } })
        } catch (error) {
          toast.error(error?.message || '修改密码失败')
        } finally {
          this.passwordSaving = false
        }
      })
    },
    async confirmRevokeAll() {
      try {
        await confirmBox(
          '将使全部设备（含当前）的登录立即失效，并需重新登录。仅在怀疑账号异常时操作。',
          '退出所有设备',
          {
            type: 'warning',
            confirmButtonText: '立即全部退出',
            cancelButtonText: '取消',
          }
        )
      } catch (e) {
        if (e === 'cancel') return
        return
      }
      this.revoking = true
      try {
        const res = await revokeAllSessions(this.authToken)
        toast.success(`已退出 ${res.revokedCount} 个会话，请重新登录`)
        useAuthStore().clearAuthState()
        this.$router.replace({ path: '/login', query: { redirect: '/settings?tab=security' } })
      } catch (error) {
        toast.error(error?.message || '操作失败')
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
  max-width: 980px;
  margin: 0 auto;
  padding: 20px 20px 64px;
}

.settings-hero {
  margin-bottom: 22px;
}

.settings-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.14em;
  color: var(--meme-primary);
}

.settings-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--meme-text);
}

.settings-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.settings-body-wrap {
  position: relative;
}

.settings-page-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--meme-radius-lg);
  background: color-mix(in srgb, var(--meme-bg-card) 82%, transparent);
  color: var(--meme-text-secondary);
  font-size: 14px;
}

.settings-page-loading__spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: sec-spin 0.8s linear infinite;
}

.settings-body.is-busy {
  pointer-events: none;
  opacity: 0.55;
}

.settings-body {
  display: grid;
  grid-template-columns: 216px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
  min-height: 320px;
}

.settings-nav-card {
  position: sticky;
  top: 76px;
}

.settings-nav-card :deep(.vs-card__text) {
  padding: 0 !important;
}

.settings-nav__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
}

.settings-nav__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 11px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 14px;
  font-weight: 650;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.settings-nav__item:hover {
  color: var(--meme-text);
  background: var(--meme-bg-muted);
}

.settings-nav__item.is-active {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--meme-primary) 22%, transparent);
}

.settings-nav__item i {
  font-size: 16px;
}

.settings-panel-card :deep(.vs-card__text) {
  padding: 0 !important;
}

.settings-panel {
  padding: 24px 28px 28px;
  background:
    radial-gradient(
      680px 240px at 100% -20%,
      color-mix(in srgb, var(--meme-primary) 12%, transparent),
      transparent 55%
    ),
    var(--meme-bg-card);
}

.settings-panel__head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 22px;
}

.settings-panel__head--inline {
  margin-bottom: 0;
  min-width: 0;
  flex: 1;
}

.settings-panel__mark {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  font-size: 18px;
  color: #fff;
  background: linear-gradient(145deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 8px 18px var(--meme-focus-ring);
}

.settings-panel__mark--shield {
  background: linear-gradient(145deg, #34d399, #059669);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--meme-success) 35%, transparent);
}

.settings-panel__title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.settings-panel__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.settings-form {
  max-width: 520px;
}

.avatar-block {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 22px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--meme-border);
  background: color-mix(in srgb, var(--meme-bg-muted) 70%, var(--meme-bg-elevated));
}

.avatar-preview-wrap {
  position: relative;
  flex-shrink: 0;
  padding: 3px;
  border-radius: 50%;
  background: linear-gradient(
    145deg,
    var(--meme-primary),
    color-mix(in srgb, var(--meme-primary) 40%, #fff)
  );
  box-shadow: 0 8px 20px var(--meme-focus-ring);
}

.avatar-preview-wrap.is-previewable {
  cursor: zoom-in;
}

.avatar-preview-wrap.is-previewable:hover .avatar-zoom-hint,
.avatar-preview-wrap.is-previewable:focus-visible .avatar-zoom-hint {
  opacity: 1;
}

.avatar-preview {
  display: block;
  border: 3px solid var(--meme-bg-elevated);
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-weight: 700;
}

.avatar-zoom-hint {
  position: absolute;
  inset: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.42);
  color: #fff;
  font-size: 22px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.avatar-actions__title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--meme-text);
}

.avatar-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.gender-segment {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
}

.gender-segment__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 76px;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.gender-segment__item:hover {
  color: var(--meme-text);
  background: color-mix(in srgb, var(--meme-bg-elevated) 70%, transparent);
}

.gender-segment__item.is-active {
  background: var(--meme-bg-elevated);
  color: var(--meme-primary);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  font-weight: 700;
}

.gender-segment__item i {
  font-size: 15px;
}

.settings-form :deep(.settings-input .vs-input__wrapper),
.settings-form :deep(.settings-input .vs-input__original) {
  min-height: 40px;
  border-radius: 12px !important;
}

.settings-form :deep(.settings-input--area .vs-input__original),
.settings-form :deep(.settings-input--area textarea) {
  min-height: 88px;
  border-radius: 12px !important;
  line-height: 1.55;
}

.settings-date {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  color: var(--meme-text);
  font-size: 14px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.settings-date:focus {
  outline: none;
  border-color: var(--meme-primary);
  background: var(--meme-bg-elevated);
  box-shadow: 0 0 0 3px var(--meme-focus-ring);
}

.form-actions {
  margin-top: 10px;
}

.form-actions--split {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.settings-btn {
  min-height: 38px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: 11px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease, box-shadow 0.15s ease,
    background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.settings-btn:disabled {
  cursor: not-allowed;
  transform: none;
  filter: none;
  opacity: 0.72;
}

.settings-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 6px 16px var(--meme-focus-ring);
}

.settings-btn--primary:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.03);
}

.settings-btn--soft {
  color: var(--meme-primary);
  background: var(--meme-bg-elevated);
  border: 1px solid color-mix(in srgb, var(--meme-primary) 28%, var(--meme-border));
  box-shadow: 0 2px 8px var(--meme-focus-ring);
}

.settings-btn--soft:hover:not(:disabled) {
  background: var(--meme-primary-soft);
  border-color: var(--meme-primary);
}

.settings-btn--ghost {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
}

.settings-btn--ghost:hover:not(:disabled) {
  color: var(--meme-text);
  background: var(--meme-bg-elevated);
}

.settings-btn--danger {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--meme-danger),
    color-mix(in srgb, var(--meme-danger) 72%, #7f1d1d)
  );
  box-shadow: 0 6px 16px color-mix(in srgb, var(--meme-danger) 32%, transparent);
}

.settings-btn--danger:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.04);
}

.is-spinning {
  display: inline-block;
  animation: sec-spin 0.8s linear infinite;
}

.sec-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.sec-health-card {
  margin-bottom: 22px;
}

.sec-health-card :deep(.vs-card__text) {
  padding: 0 !important;
}

.sec-health-card.is-ok {
  border-color: color-mix(in srgb, var(--meme-success) 30%, var(--meme-border)) !important;
  background: var(--meme-success-soft) !important;
}

.sec-health-card.is-warn {
  border-color: color-mix(in srgb, var(--meme-warning) 30%, var(--meme-border)) !important;
  background: var(--meme-warning-soft) !important;
}

.sec-health-card.is-risk {
  border-color: color-mix(in srgb, var(--meme-danger) 30%, var(--meme-border)) !important;
  background: var(--meme-danger-soft) !important;
}

.sec-health {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 16px 18px;
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

.sec-health-card.is-ok .sec-health__icon {
  color: var(--meme-success);
}

.sec-health-card.is-warn .sec-health__icon {
  color: var(--meme-warning);
}

.sec-health-card.is-risk .sec-health__icon {
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

.sec-health__text {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
}

.sec-health__progress :deep(.ui-progress__bar) {
  background: var(--meme-primary);
}

.sec-health__progress.is-ok :deep(.ui-progress__bar) {
  background: var(--meme-success);
}

.sec-health__progress.is-warn :deep(.ui-progress__bar) {
  background: var(--meme-warning);
}

.sec-health__progress.is-risk :deep(.ui-progress__bar) {
  background: var(--meme-danger);
}

.sec-section {
  margin-bottom: 22px;
}

.sec-section__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--meme-text-muted);
  text-transform: uppercase;
}

.sec-section__title i {
  font-size: 15px;
  color: var(--meme-primary);
}

.sec-list-card :deep(.vs-card__text),
.sec-password-card :deep(.vs-card__text),
.sec-danger-card :deep(.vs-card__text) {
  padding: 0 !important;
}

.sec-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.sec-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
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
  gap: 5px;
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
}

.sec-action-btn--muted {
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
  color: var(--meme-text-secondary);
}

.sec-action-btn--muted:hover {
  color: var(--meme-text);
  border-color: var(--meme-border-strong);
}

.sec-password-card {
  margin-top: 12px;
}

.sec-password {
  padding: 16px 16px 8px;
}

.sec-password__alert {
  margin-bottom: 14px;
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

.sec-danger-card {
  border-color: color-mix(in srgb, var(--meme-danger) 28%, var(--meme-border)) !important;
  background: var(--meme-danger-soft) !important;
}

.sec-danger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
}

.sec-danger__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}

.sec-danger__icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 20px;
  color: #fff;
  background: linear-gradient(
    145deg,
    var(--meme-danger),
    color-mix(in srgb, var(--meme-danger) 70%, #7f1d1d)
  );
  box-shadow: 0 6px 14px color-mix(in srgb, var(--meme-danger) 30%, transparent);
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

  .settings-nav-card {
    position: static;
  }

  .settings-nav__list {
    flex-direction: row;
    padding: 10px;
  }

  .settings-nav__item {
    flex: 1;
    justify-content: center;
  }

  .settings-panel {
    padding: 20px 16px 24px;
  }

  .avatar-block {
    flex-direction: column;
    align-items: flex-start;
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

.settings-avatar-viewer :deep(.vs-dialog__content) {
  background: transparent !important;
  box-shadow: none !important;
}

.settings-avatar-viewer__body {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 48px 24px 32px;
  background: rgba(15, 23, 42, 0.88);
  cursor: zoom-out;
}

.settings-avatar-viewer__close {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 22px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.settings-avatar-viewer__close:hover {
  background: rgba(255, 255, 255, 0.22);
}

.settings-avatar-viewer__img {
  max-width: min(92vw, 720px);
  max-height: 82vh;
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  cursor: default;
}

.settings-avatar-viewer__tip {
  margin: 16px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}
</style>
