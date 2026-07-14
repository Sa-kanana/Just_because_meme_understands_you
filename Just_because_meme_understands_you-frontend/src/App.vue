<template>
  <div id="app">
    <header class="site-header">
      <div class="header-left">
        <router-link to="/" class="logo-wrap">
          <img class="logo-img" alt="网站 logo" src="@/assets/icon.jpg" />
          <span class="logo-text">只因“梗”懂你</span>
        </router-link>
        <nav class="nav-links">
          <router-link
            :to="helpNavLocation"
            class="nav-link"
            :class="{ active: $route.name === 'help' }"
          >
            帮助文档
          </router-link>
        </nav>
      </div>

      <div class="header-center">
        <el-input
          ref="headerSearchRef"
          v-model="searchKeyword"
          placeholder="搜索梗图、标签..."
          clearable
          class="header-search"
          @keyup.enter="handleSearch"
        >
          <template #suffix>
            <el-icon class="search-suffix-icon" @click="handleSearch"><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <div class="header-right">
        <button
          type="button"
          class="header-theme-btn"
          :title="themeBtnTitle"
          :aria-label="themeBtnTitle"
          @click="toggleTheme"
        >
          <i :class="themeBtnIcon" aria-hidden="true" />
        </button>
        <!-- 未登录：显示 登录/注册 -->
        <template v-if="!currentUser">
          <router-link :to="loginRoute" class="header-auth-link">登录</router-link>
          <span class="header-auth-sep">/</span>
          <router-link to="/register" class="header-auth-link">注册</router-link>
        </template>
        <!-- 已登录：通知图标（小红点）+ 用户头像（悬停下拉菜单） -->
        <template v-else>
          <el-badge :is-dot="hasNotification" class="header-notification-badge">
            <el-icon class="header-notification-icon" :size="22" @click="goNotifications">
              <Bell />
            </el-icon>
          </el-badge>
          <el-dropdown
            trigger="hover"
            placement="bottom-end"
            @command="handleUserMenuCommand"
          >
            <span class="header-avatar-wrapper">
              <el-avatar
                :size="36"
                :src="currentUser.avatar"
                class="header-avatar"
              >
                {{ currentUser.nickname ? currentUser.nickname.charAt(0).toUpperCase() : 'U' }}
              </el-avatar>
            </span>
            <template #dropdown>
              <el-dropdown-menu class="user-dropdown-menu">
                <el-dropdown-item command="publish">
                  <i class="ri-add-line user-dropdown-icon"></i>
                  <span>发布梗</span>
                </el-dropdown-item>
                <el-dropdown-item command="profile">
                  <i class="ri-user-3-line user-dropdown-icon"></i>
                  <span>个人主页</span>
                </el-dropdown-item>
        
                <el-dropdown-item command="account">
                  <i class="ri-settings-3-line user-dropdown-icon"></i>
                  <span>账号设置</span>
                </el-dropdown-item>
                <el-dropdown-item command="feedback">
                  <i class="ri-chat-1-line user-dropdown-icon"></i>
                  <span>提交反馈/建议</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <i class="ri-logout-box-r-line user-dropdown-icon"></i>
                  <span>退出账号</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </div>
    </header>

    <!-- 顶部加载进度条：路由切换时展示，模拟进度更灵动 -->
    <div
      v-show="isRouteLoading"
      class="route-progress-wrap"
    >
      <div class="route-progress-inner">
        <el-progress
          :percentage="progressPercent"
          :show-text="false"
          :stroke-width="4"
          class="route-progress-bar"
        />
      </div>
    </div>

    <main class="page-main">
      <GlobalBreadcrumb />
      <router-view />

      <!-- 回到顶部按钮 -->
      <el-backtop
        :right="32"
        :bottom="40"
        :visibility-height="240"
        class="meme-backtop"
      >
        <div class="meme-backtop-inner">
          <el-icon><ArrowUp /></el-icon>
        </div>
      </el-backtop>
    </main>

    <Teleport to="body">
      <Transition name="logout-fade">
        <div
          v-if="logoutConfirmVisible"
          class="logout-overlay"
          role="presentation"
          @click.self="cancelLogout"
          @keydown.esc="cancelLogout"
        >
          <div
            class="logout-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="logout-dialog-title"
          >
            <div class="logout-dialog__visual" aria-hidden="true">
              <span class="logout-dialog__ring" />
              <span class="logout-dialog__icon-wrap">
                <i class="ri-logout-box-r-line logout-dialog__icon" />
              </span>
            </div>
            <h3 id="logout-dialog-title" class="logout-dialog__title">退出登录</h3>
            <p class="logout-dialog__desc">退出后仍可浏览梗图，发布与收藏等需重新登录。</p>
            <div class="logout-dialog__actions">
              <button
                type="button"
                class="logout-dialog__btn logout-dialog__btn--ghost"
                :disabled="logoutLoading"
                @click="cancelLogout"
              >
                再想想
              </button>
              <button
                type="button"
                class="logout-dialog__btn logout-dialog__btn--danger"
                :disabled="logoutLoading"
                @click="confirmLogout"
              >
                {{ logoutLoading ? '退出中…' : '退出登录' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

  </div>
</template>

<script>
import { Search, Bell, ArrowUp } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import {
  buildUserProfileLocation,
  buildSearchLocation,
  buildToolPageLocation,
} from '@/utils/pageBreadcrumb'
import GlobalBreadcrumb from '@/components/layout/GlobalBreadcrumb.vue'

export default {
  name: 'App',
  components: { Search, Bell, ArrowUp, GlobalBreadcrumb },
  data() {
    return {
      searchKeyword: '',
      isRouteLoading: false,
      progressPercent: 0,
      progressTimer: null,
      progressTweenTimer: null,
      /** 是否有未读通知（可后续对接接口） */
      hasNotification: true,
      logoutConfirmVisible: false,
      logoutLoading: false,
    }
  },
  computed: {
    currentUser() {
      return useAuthStore().currentUser
    },
    themeIsDark() {
      return useThemeStore().isDark
    },
    themeBtnIcon() {
      return this.themeIsDark ? 'ri-sun-line' : 'ri-moon-line'
    },
    themeBtnTitle() {
      return this.themeIsDark ? '切换到浅色' : '切换到深色'
    },
    helpNavLocation() {
      const patch = useBreadcrumbStore().patch || {}
      return buildToolPageLocation('help', {
        fromRoute: this.$route,
        memeName: patch.meme?.name || '',
        profileName: patch.nickname || '',
      })
    },
    loginRoute() {
      const isGuestPage =
        this.$route.path === '/login' ||
        this.$route.path === '/register' ||
        this.$route.path === '/forgot-password'
      const rawRedirect = this.$route.query.redirect || (isGuestPage ? '/' : this.$route.fullPath) || '/'
      const redirect = String(rawRedirect)
      return {
        path: '/login',
        query: {
          redirect,
        },
      }
    },
  },
  mounted() {
    if (this.$route.query.keyword != null) {
      this.searchKeyword = this.$route.query.keyword
    }
    window.addEventListener('route-loading', this.handleRouteLoading)
    this.startProgress()
    setTimeout(() => this.finishProgress(), 500)
  },
  beforeUnmount() {
    window.removeEventListener('route-loading', this.handleRouteLoading)
    this.clearProgressTimers()
  },
  watch: {
    '$route.query.keyword'(val) {
      if (val != null) this.searchKeyword = val
    },
    '$route'(to) {
      if (to.name === 'search' && String(to.query.focus || '') === '1') {
        this.$nextTick(() => {
          this.focusHeaderSearch()
          const query = { ...to.query }
          delete query.focus
          this.$router.replace({ name: 'search', query })
        })
      }
    },
  },
  methods: {
    focusHeaderSearch() {
      const input = this.$refs.headerSearchRef
      if (input && typeof input.focus === 'function') {
        input.focus()
        return
      }
      const el = input?.$el?.querySelector('input')
      el?.focus()
    },
    handleSearch() {
      const keyword = this.searchKeyword?.trim() || ''
      this.$router.push(
        buildSearchLocation({
          keyword,
          from: 'header',
        })
      )
    },
    handleUserMenuCommand(command) {
      switch (command) {
        case 'publish':
          this.goPublish()
          break
        case 'profile':
          this.goProfile()
          break
        case 'feedback':
          this.goFeedback()
          break
        case 'account':
          this.goAccountSettings()
          break
        case 'logout':
          this.handleLogout()
          break
        default:
          break
      }
    },
    goProfile() {
      const authStore = useAuthStore()
      const currentUser = authStore.currentUser
      const rawUserId =
        currentUser && currentUser.id != null
          ? currentUser.id
          : currentUser && currentUser.userId != null
            ? currentUser.userId
            : ''
      const userId = rawUserId != null ? String(rawUserId).trim() : ''
      if (!userId || !/^\d+$/.test(userId)) {
        this.$message &&
          this.$message.warning &&
          this.$message.warning('登录态中的用户ID异常，请重新登录后再试')
        this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
        return
      }
      const patch = useBreadcrumbStore().patch || {}
      const memeName =
        (patch.meme && patch.meme.name) || this.$route.query.memeName || ''
      this.$router.push(
        buildUserProfileLocation(userId, {
          fromRoute: this.$route,
          memeName,
          profileName: patch.nickname || '',
        })
      )
    },
    goPublish() {
      const authStore = useAuthStore()
      if (!authStore.isLoggedIn) {
        this.$router.push({ name: 'login', query: { redirect: this.$route.fullPath } })
        return
      }
      const patch = useBreadcrumbStore().patch || {}
      this.$router.push(
        buildToolPageLocation('publishMeme', {
          fromRoute: this.$route,
          memeName: patch.meme?.name || '',
          profileName: patch.nickname || '',
        })
      )
    },
    goFeedback() {
      const patch = useBreadcrumbStore().patch || {}
      this.$router.push(
        buildToolPageLocation('help', {
          fromRoute: this.$route,
          memeName: patch.meme?.name || '',
          profileName: patch.nickname || '',
        })
      )
    },
    goAccountSettings() {
      const patch = useBreadcrumbStore().patch || {}
      this.$router.push(
        buildToolPageLocation('accountSettings', {
          fromRoute: this.$route,
          memeName: patch.meme?.name || '',
          profileName: patch.nickname || '',
        })
      )
    },
    toggleTheme() {
      useThemeStore().toggleLightDark()
    },
    goNotifications() {
      // 可跳转通知页或打开通知列表
      this.$router.push({ path: '/' })
    },
    handleLogout() {
      this.logoutConfirmVisible = true
    },
    cancelLogout() {
      if (this.logoutLoading) return
      this.logoutConfirmVisible = false
    },
    async confirmLogout() {
      if (this.logoutLoading) return
      this.logoutLoading = true
      try {
        await useAuthStore().logout()
        this.logoutConfirmVisible = false
        this.$router.replace({ path: '/' })
      } finally {
        this.logoutLoading = false
      }
    },
    handleRouteLoading(event) {
      const detail = event && event.detail
      const loading = !!(detail && detail.loading)
      if (loading) {
        this.startProgress()
      } else {
        this.finishProgress()
      }
    },
    startProgress() {
      this.clearProgressTimers()
      this.isRouteLoading = true
      this.progressPercent = 0
      const steps = [20, 45, 65, 80]
      let stepIndex = 0
      this.progressTimer = setInterval(() => {
        if (stepIndex < steps.length) {
          this.progressPercent = steps[stepIndex]
          stepIndex += 1
        }
      }, 120)
    },
    finishProgress() {
      this.clearProgressTimers()
      this.progressPercent = 100
      this.progressTweenTimer = setTimeout(() => {
        this.isRouteLoading = false
        this.progressPercent = 0
      }, 320)
    },
    clearProgressTimers() {
      if (this.progressTimer) {
        clearInterval(this.progressTimer)
        this.progressTimer = null
      }
      if (this.progressTweenTimer) {
        clearTimeout(this.progressTweenTimer)
        this.progressTweenTimer = null
      }
    },
  },
}
</script>

<style>
/* Token / 基础样式见 src/styles/tokens.css、base.css */

.site-header {
  height: 56px;
  padding: 0 24px;
  display: grid;
  grid-template-columns: auto minmax(220px, 360px) auto;
  column-gap: 24px;
  align-items: center;
  background-color: var(--meme-bg-card);
  border-bottom: 1px solid var(--meme-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.header-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.header-theme-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--meme-border);
  border-radius: 10px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 18px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.header-theme-btn:hover {
  color: var(--meme-primary);
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.header-auth-link {
  color: var(--meme-primary);
  font-size: 15px;
  font-weight: 500;
  text-decoration: none;
  padding: 6px 12px;
  border-radius: var(--meme-radius-sm, 6px);
  transition: color 0.2s ease, background-color 0.2s ease;
}
.header-auth-link:hover {
  color: var(--meme-primary-dark);
  background-color: var(--el-color-primary-light-9, #e8f2fd);
}
.header-auth-link.router-link-active {
  color: var(--meme-primary);
  background-color: var(--meme-primary-soft);
}
.header-auth-sep {
  color: var(--meme-text-secondary);
  font-size: 14px;
  font-weight: 400;
  user-select: none;
  margin: 0 2px;
}

.header-notification-badge {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
.header-notification-icon {
  color: var(--meme-text-secondary);
  transition: color 0.2s ease;
}
.header-notification-icon:hover {
  color: var(--meme-primary);
}

.logo-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  background-color: #000;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--meme-text);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 16px;
}

.nav-link {
  font-size: 18px;
  color: var(--meme-text-secondary);
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 6px;
  transition: color 0.15s ease, background-color 0.15s ease, transform 0.05s ease;
}

.nav-link:hover {
  color: var(--meme-primary);
  background-color: var(--meme-bg-muted);
}

.nav-link.active {
  color: var(--meme-primary);
  background-color: var(--meme-border);
}

.nav-link:active {
  color: var(--meme-primary);
  background-color: var(--meme-border);
  transform: scale(0.97);
}

.header-search {
  width: 100%;
  max-width: 360px;
}

.header-search :deep(.el-input__wrapper) {
  border-radius: 999px;
  box-shadow: 0 0 0 1px var(--meme-border) inset;
}

.header-search :deep(.el-input__wrapper:hover),
.header-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--meme-primary) inset;
}

.search-suffix-icon {
  cursor: pointer;
  color: var(--meme-text-muted);
  transition: color 0.2s ease, transform 0.2s ease;
}

.search-suffix-icon:hover {
  color: var(--meme-primary);
  transform: scale(1.1);
}

.icon-button {
  border: none;
  background: transparent;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
  transition: background-color 0.15s ease, transform 0.1s ease;
}

.icon-button:hover {
  background-color: var(--meme-bg-muted);
  transform: translateY(-1px);
}

.header-avatar,
.header-avatar:focus,
.header-avatar:focus-visible {
  outline: none;
  box-shadow: none;
}

.header-avatar-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.user-dropdown-menu {
  min-width: 180px;
}

.user-dropdown-menu .el-dropdown-menu__item {
  font-size: 14px;
}

.user-dropdown-menu .el-dropdown-menu__item:hover {
  background-color: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.user-dropdown-menu .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-dropdown-icon {
  font-size: 16px;
}


/* 右上角悬浮头像：点击展开名片浮窗 */
.header-avatar {
  cursor: pointer;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  color: var(--meme-text-inverse);
  font-weight: 600;
  font-size: 16px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  flex-shrink: 0;
  border-radius: 50%;
}
.header-avatar:hover {
  transform: scale(1.06);
  box-shadow: 0 4px 14px var(--meme-focus-ring);
}

/* 去掉 Element Plus 下拉触发元素在聚焦时的黑色描边 */
#app .el-tooltip__trigger:focus,
#app .el-tooltip__trigger:focus-visible,
#app .el-dropdown-selfdefine:focus,
#app .el-dropdown-selfdefine:focus-visible {
  outline: none;
  box-shadow: none;
}

.meme-backtop {
  z-index: 10000;
}

.meme-backtop-inner {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--meme-text-inverse);
  box-shadow: 0 6px 18px var(--meme-focus-ring);
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.2s ease;
}

.meme-backtop-inner .el-icon {
  font-size: 18px;
}

.meme-backtop-inner:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px var(--meme-focus-ring);
}

.page-main {
  padding: 16px 32px 32px;
}

/* 顶部加载进度条：贴顶、细条、带过渡与高光 */
.route-progress-wrap {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  background-color: transparent;
  padding: 0;
  pointer-events: none;
}

.route-progress-inner {
  width: 100%;
  overflow: hidden;
}

.route-progress-bar :deep(.el-progress-bar__outer) {
  border-radius: 0;
}

.route-progress-bar :deep(.el-progress-bar__inner) {
  border-radius: 0;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 8px rgba(49, 138, 239, 0.5);
  background-color: var(--meme-primary);
}

.page-title {
  margin: 0 0 12px;
  font-size: 28px;
  font-weight: 600;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--meme-text-secondary);
}

/* 全局链接：去除默认下划线与蓝色，继承文字样式 */
a {
  text-decoration: none;
  color: inherit;
}

a:hover,
a:focus,
a:visited {
  text-decoration: none;
  color: inherit;
}

/* 梗图卡片链接再加一层保险，防止被其他样式覆盖 */
a.meme-card-link {
  text-decoration: none;
  color: inherit;
}

a.meme-card-link:hover,
a.meme-card-link:focus,
a.meme-card-link:visited {
  text-decoration: none;
  color: inherit;
}

/* 名片浮窗（el-popover 挂载在 body，需全局样式，与页面主题一致） */
.profile-card-popover {
  padding: 0;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-md);
  background-color: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-card);
  overflow: hidden;
}
.profile-card-popover .profile-card {
  padding: 16px 20px 20px;
  min-width: 240px;
  background: linear-gradient(to bottom, var(--el-color-primary-light-9) 0%, var(--meme-bg-card) 24%);
}
.profile-card-popover .profile-card-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background-color: var(--el-color-primary-light-9);
  color: var(--meme-primary);
  font-size: 12px;
  margin-bottom: 12px;
  letter-spacing: 0.02em;
}
.profile-card-popover .profile-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--meme-border);
}
.profile-card-popover .profile-card-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  color: var(--meme-text-inverse);
  font-weight: 600;
  font-size: 22px;
}
.profile-card-popover .profile-card-info {
  min-width: 0;
}
.profile-card-popover .profile-card-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--meme-text);
  margin-bottom: 4px;
  letter-spacing: 0.02em;
}
.profile-card-popover .profile-card-signature,
.profile-card-popover .profile-card-role {
  font-size: 12px;
  color: var(--meme-text-secondary);
  line-height: 1.4;
}
.profile-card-popover .profile-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.profile-card-popover .profile-card-actions .el-button--primary {
  box-shadow: 0 2px 6px rgba(49, 138, 239, 0.3);
}
.profile-card-popover .profile-card-actions .el-button.is-round {
  border-radius: 999px;
}

/* 退出登录确认 */
.logout-overlay {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--meme-overlay);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.logout-dialog {
  width: min(100%, 360px);
  padding: 28px 28px 24px;
  border-radius: 18px;
  background:
    var(--meme-gradient-dialog),
    var(--meme-bg-card);
  border: 1px solid var(--meme-border);
  box-shadow: var(--meme-shadow-dialog);
  text-align: center;
  transform-origin: center;
}

.logout-dialog__visual {
  position: relative;
  width: 72px;
  height: 72px;
  margin: 0 auto 18px;
}

.logout-dialog__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--meme-primary-soft);
  animation: logout-pulse 2.2s ease-in-out infinite;
}

.logout-dialog__icon-wrap {
  position: absolute;
  inset: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(145deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 8px 20px var(--meme-focus-ring);
}

.logout-dialog__icon {
  font-size: 28px;
  line-height: 1;
  color: var(--meme-text-inverse);
}

.logout-dialog__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--meme-text);
}

.logout-dialog__desc {
  margin: 0 0 24px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.logout-dialog__actions {
  display: flex;
  gap: 12px;
}

.logout-dialog__btn {
  flex: 1;
  height: 42px;
  border: none;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, background 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}

.logout-dialog__btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.logout-dialog__btn:not(:disabled):active {
  transform: scale(0.98);
}

.logout-dialog__btn--ghost {
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
}

.logout-dialog__btn--ghost:not(:disabled):hover {
  background: var(--meme-border);
}

.logout-dialog__btn--danger {
  color: var(--meme-text-inverse);
  background: linear-gradient(135deg, #ef6b6b, var(--meme-danger));
  box-shadow: 0 6px 16px rgba(225, 29, 72, 0.28);
}

.logout-dialog__btn--danger:not(:disabled):hover {
  box-shadow: 0 8px 20px rgba(225, 29, 72, 0.36);
}

.logout-fade-enter-active,
.logout-fade-leave-active {
  transition: opacity 0.22s ease;
}

.logout-fade-enter-active .logout-dialog,
.logout-fade-leave-active .logout-dialog {
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.logout-fade-enter-from,
.logout-fade-leave-to {
  opacity: 0;
}

.logout-fade-enter-from .logout-dialog,
.logout-fade-leave-to .logout-dialog {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}

@keyframes logout-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.08);
    opacity: 0.72;
  }
}
</style>
