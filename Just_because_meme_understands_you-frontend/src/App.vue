<template>
  <div id="app">
    <header class="site-header" :class="{ 'is-scrolled': headerScrolled }">
      <div class="header-left">
        <router-link to="/" class="logo-wrap">
          <img class="logo-img" alt="网站 logo" src="/icon.svg?v=4" />
          <span class="logo-text">只因“梗”懂你</span>
        </router-link>
        <nav class="nav-links">
          <router-link
            :to="aiNavLocation"
            class="nav-link"
            :class="{ active: $route.name === 'aiSearch' }"
          >
            AI 搜梗
          </router-link>
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
        <vs-input
          ref="headerSearchRef"
          v-model="searchKeyword"
          placeholder="搜索梗图、标签..."
          clearable
          block
          icon-after
          shape="rounded"
          class="header-search"
          @keydown.enter="handleSearch"
          @click-icon="handleSearch"
        >
          <template #icon>
            <i class="ri-search-line search-suffix-icon" aria-hidden="true" />
          </template>
        </vs-input>
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
        <template v-if="!currentUser">
          <div class="header-auth">
            <vs-button
              type="border"
              color="primary"
              size="small"
              class="header-auth__login"
              @click="$router.push(loginRoute)"
            >
              登录
            </vs-button>
            <vs-button
              color="primary"
              size="small"
              class="header-auth__register"
              @click="$router.push('/register')"
            >
              注册
            </vs-button>
          </div>
        </template>
        <template v-else>
          <HeaderNotificationDropdown />
          <ui-dropdown
            trigger="hover"
            placement="bottom-end"
            @command="handleUserMenuCommand"
          >
            <span class="header-avatar-wrapper">
              <ui-avatar
                :size="34"
                :src="currentUser.avatar"
                :fallback="currentUser.nickname || currentUser.username || 'U'"
                pointer
                class="header-avatar"
              />
            </span>
            <template #dropdown>
              <ui-dropdown-menu class="user-dropdown-menu">
                <ui-dropdown-item command="publish">
                  <i class="ri-add-line user-dropdown-icon"></i>
                  <span>发布梗</span>
                </ui-dropdown-item>
                <ui-dropdown-item command="ai">
                  <i class="ri-robot-2-line user-dropdown-icon"></i>
                  <span>AI 搜梗</span>
                </ui-dropdown-item>
                <ui-dropdown-item command="profile">
                  <i class="ri-user-3-line user-dropdown-icon"></i>
                  <span>个人主页</span>
                </ui-dropdown-item>
                <ui-dropdown-item command="account">
                  <i class="ri-settings-3-line user-dropdown-icon"></i>
                  <span>账号设置</span>
                </ui-dropdown-item>
                <ui-dropdown-item command="feedback">
                  <i class="ri-chat-1-line user-dropdown-icon"></i>
                  <span>提交反馈/建议</span>
                </ui-dropdown-item>
                <ui-dropdown-item divided command="logout">
                  <i class="ri-logout-box-r-line user-dropdown-icon"></i>
                  <span>退出账号</span>
                </ui-dropdown-item>
              </ui-dropdown-menu>
            </template>
          </ui-dropdown>
        </template>
      </div>
    </header>

    <!-- 顶部加载进度条：路由切换时展示，模拟进度更灵动 -->
    <div
      v-show="isRouteLoading"
      class="route-progress-wrap"
    >
      <div class="route-progress-inner">
        <ui-progress
          :percentage="progressPercent"
          :stroke-width="4"
          class="route-progress-bar"
        />
      </div>
    </div>

    <main class="page-main">
      <GlobalBreadcrumb />
      <router-view />

      <!-- 回到顶部按钮 -->
      <ui-backtop
        :right="32"
        :bottom="40"
        :visibility-height="240"
        class="meme-backtop"
      >
        <div class="meme-backtop-inner">
          <i class="ri-arrow-up-line" />
        </div>
      </ui-backtop>
    </main>

    <vs-dialog
      v-model="logoutConfirmVisible"
      width="360px"
      class="logout-dialog"
      :prevent-close="logoutLoading"
      @close="cancelLogout"
    >
      <div class="logout-dialog__body">
        <div class="logout-dialog__visual" aria-hidden="true">
          <span class="logout-dialog__ring" />
          <span class="logout-dialog__icon-wrap">
            <i class="ri-logout-box-r-line logout-dialog__icon" />
          </span>
        </div>
        <h3 class="logout-dialog__title">退出登录</h3>
        <p class="logout-dialog__desc">
          退出后仍可浏览梗图，发布与收藏等需重新登录。
        </p>
      </div>

      <template #footer>
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
            <i
              v-if="logoutLoading"
              class="ri-loader-4-line logout-dialog__btn-spin"
              aria-hidden="true"
            />
            {{ logoutLoading ? '退出中...' : '退出登录' }}
          </button>
        </div>
      </template>
    </vs-dialog>

    <UiConfirmDialog />
  </div>
</template>

<script>
import { toast } from '@/utils/uiFeedback'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import { useNotificationStore } from '@/stores/notification'
import { useAiChatStore } from '@/stores/aiChat'
import {
  buildUserProfileLocation,
  buildSearchLocation,
  buildToolPageLocation,
} from '@/utils/pageBreadcrumb'
import GlobalBreadcrumb from '@/components/layout/GlobalBreadcrumb.vue'
import HeaderNotificationDropdown from '@/components/notification/HeaderNotificationDropdown.vue'

export default {
  name: 'App',
  components: { GlobalBreadcrumb, HeaderNotificationDropdown },
  data() {
    return {
      searchKeyword: '',
      isRouteLoading: false,
      progressPercent: 0,
      progressTimer: null,
      progressTweenTimer: null,
      logoutConfirmVisible: false,
      logoutLoading: false,
      headerScrolled: false,
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
    aiNavLocation() {
      const authStore = useAuthStore()
      if (!authStore.isLoggedIn) {
        return {
          path: '/login',
          query: { redirect: '/ai' },
        }
      }
      const patch = useBreadcrumbStore().patch || {}
      return buildToolPageLocation('aiSearch', {
        fromRoute: this.$route,
        memeName: patch.meme?.name || '',
        profileName: patch.nickname || '',
        from: 'header',
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
    window.addEventListener('scroll', this.onHeaderScroll, { passive: true })
    this.onHeaderScroll()
    this.startProgress()
    setTimeout(() => this.finishProgress(), 500)
    this.refreshNotificationBadge()
  },
  beforeUnmount() {
    window.removeEventListener('route-loading', this.handleRouteLoading)
    window.removeEventListener('scroll', this.onHeaderScroll)
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
      if (useAuthStore().isLoggedIn) {
        this.refreshNotificationBadge()
      }
    },
    currentUser(val, oldVal) {
      if (val && !oldVal) {
        this.refreshNotificationBadge()
      }
      if (!val) {
        useNotificationStore().reset()
        useAiChatStore().reset()
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
        case 'ai':
          this.goAiSearch()
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
        toast.warning('登录态中的用户ID异常，请重新登录后再试')
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
    goAiSearch() {
      const authStore = useAuthStore()
      if (!authStore.isLoggedIn) {
        this.$router.push({ name: 'login', query: { redirect: '/ai' } })
        return
      }
      const patch = useBreadcrumbStore().patch || {}
      this.$router.push(
        buildToolPageLocation('aiSearch', {
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
    refreshNotificationBadge() {
      const authStore = useAuthStore()
      if (!authStore.isLoggedIn) return
      useNotificationStore()
        .refreshUnreadCount()
        .catch(() => {})
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
    onHeaderScroll() {
      this.headerScrolled = window.scrollY > 8
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
  position: sticky;
  top: 0;
  z-index: 100;
  height: 58px;
  padding: 0 28px;
  display: grid;
  grid-template-columns: auto minmax(220px, 380px) auto;
  column-gap: 24px;
  align-items: center;
  background: color-mix(in srgb, var(--meme-bg-elevated) 88%, transparent);
  border-bottom: 1px solid transparent;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.site-header.is-scrolled {
  border-bottom-color: var(--meme-border);
  box-shadow: var(--meme-shadow-soft);
  background: color-mix(in srgb, var(--meme-bg-elevated) 94%, transparent);
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

.header-auth {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.header-auth__login.vs-button,
.header-auth__register.vs-button {
  height: 34px !important;
  min-height: 34px !important;
  padding: 0 14px !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  font-weight: 650 !important;
  box-shadow: none !important;
  transform: none !important;
  filter: none !important;
}

.header-auth__login.vs-button {
  color: var(--meme-primary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border)) !important;
}

.header-auth__login.vs-button:hover:not(:disabled) {
  color: var(--meme-primary-dark) !important;
  background: var(--meme-primary-soft) !important;
  border-color: var(--meme-primary) !important;
  filter: none !important;
  transform: none !important;
}

.header-auth__register.vs-button {
  color: #fff !important;
  background: var(--meme-primary) !important;
  border: 1px solid var(--meme-primary) !important;
  box-shadow: 0 2px 8px var(--meme-focus-ring) !important;
}

.header-auth__register.vs-button:hover:not(:disabled) {
  color: #fff !important;
  background: var(--meme-primary-dark) !important;
  border-color: var(--meme-primary-dark) !important;
  box-shadow: 0 4px 12px var(--meme-focus-ring) !important;
  filter: none !important;
  transform: none !important;
}

.header-auth__login.vs-button :deep(.vs-button__content),
.header-auth__login.vs-button :deep(.vs-button__text),
.header-auth__register.vs-button :deep(.vs-button__content),
.header-auth__register.vs-button :deep(.vs-button__text) {
  color: inherit !important;
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
  border-radius: 0;
  background: transparent;
  display: block;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  position: relative;
  font-size: 14px;
  font-weight: 500;
  color: var(--meme-text-secondary);
  text-decoration: none;
  padding: 8px 12px;
  border-radius: var(--meme-radius-sm);
  transition: color 0.15s ease, background-color 0.15s ease;
}

.nav-link:hover {
  color: var(--meme-primary);
  background-color: var(--meme-primary-soft);
}

.nav-link.active {
  color: var(--meme-primary);
  background-color: var(--meme-primary-soft);
}

.nav-link.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 4px;
  height: 2px;
  border-radius: 1px;
  background: var(--meme-primary);
}

.nav-link:active {
  transform: scale(0.98);
}

.header-search {
  width: 100%;
  max-width: 380px;
}

.header-search :deep(.vs-input__wrapper) {
  height: 38px;
  border-radius: 999px !important;
  background: var(--meme-bg-muted) !important;
  box-shadow: 0 0 0 1px var(--meme-border) inset;
  transition: box-shadow 0.18s ease, background 0.18s ease;
}

.header-search :deep(.vs-input__original) {
  width: 100% !important;
  height: 38px;
  padding: 0 42px 0 16px !important;
  border: none !important;
  border-radius: 999px !important;
  background: transparent !important;
  color: var(--meme-text) !important;
  font-size: 14px;
  line-height: 38px;
  box-shadow: none !important;
}

.header-search :deep(.vs-input__original::placeholder) {
  color: var(--meme-text-muted);
  opacity: 0.85;
}

.header-search :deep(.vs-input__placeholder) {
  left: 16px !important;
  font-size: 14px;
  color: var(--meme-text-muted);
  opacity: 0.85;
}

.header-search :deep(.vs-input__icon) {
  width: 32px !important;
  height: 32px !important;
  right: 3px !important;
  left: auto !important;
  border-radius: 999px !important;
  background: transparent !important;
  box-shadow: none !important;
  color: var(--meme-text-muted);
  transform: none !important;
  transition: color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.header-search :deep(.vs-input__icon:hover),
.header-search :deep(.vs-input.is-focus .vs-input__icon) {
  color: var(--meme-primary);
  background: var(--meme-primary-soft) !important;
  box-shadow: none !important;
  transform: none !important;
}

.header-search :deep(.vs-input__clearable) {
  right: 38px !important;
  background: color-mix(in srgb, var(--meme-text-muted) 16%, transparent) !important;
}

.header-search:hover :deep(.vs-input__wrapper),
.header-search :deep(.vs-input.is-hovering .vs-input__wrapper) {
  background: var(--meme-bg-elevated) !important;
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border)) inset;
}

.header-search :deep(.vs-input.is-focus .vs-input__wrapper) {
  background: var(--meme-bg-elevated) !important;
  box-shadow:
    0 0 0 1px var(--meme-primary) inset,
    0 0 0 3px var(--meme-focus-ring) !important;
}

.header-search :deep(.vs-input.is-focus .vs-input__original) {
  padding-left: 16px !important;
  background: transparent !important;
}

.search-suffix-icon {
  font-size: 16px;
  line-height: 1;
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
  border-radius: 50%;
  border: 2px solid var(--meme-border);
  transition: border-color 0.15s ease, box-shadow 0.2s ease, transform 0.15s ease;
}

.header-avatar-wrapper:hover {
  border-color: var(--meme-primary);
  box-shadow: 0 4px 14px var(--meme-focus-ring);
  transform: translateY(-1px);
}

.user-dropdown-menu {
  min-width: 180px;
}

.user-dropdown-menu :deep(.ui-dropdown-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.user-dropdown-menu :deep(.ui-dropdown-item:hover:not(:disabled)) {
  background-color: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.user-dropdown-icon {
  font-size: 16px;
}


/* 右上角悬浮头像 */
.header-avatar {
  cursor: pointer;
  flex-shrink: 0;
}

.header-avatar:hover {
  transform: none;
  box-shadow: none;
}

/* 去掉下拉触发元素在聚焦时的黑色描边 */
#app .ui-dropdown__trigger:focus,
#app .ui-dropdown__trigger:focus-visible {
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

.meme-backtop-inner i {
  font-size: 18px;
}

.meme-backtop-inner:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px var(--meme-focus-ring);
}

.page-main {
  padding: 8px 24px 28px;
  max-width: 1280px;
  margin: 0 auto;
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

.route-progress-bar :deep(.ui-progress) {
  height: 4px;
  border-radius: 0;
}

.route-progress-bar :deep(.ui-progress__bar) {
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

/* 名片浮窗（挂载在 body，需全局样式，与页面主题一致） */
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
  background: linear-gradient(to bottom, var(--meme-primary-soft) 0%, var(--meme-bg-card) 24%);
}
.profile-card-popover .profile-card-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background-color: var(--meme-primary-soft);
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
.profile-card-popover .profile-card-actions .vs-button {
  box-shadow: 0 2px 6px rgba(49, 138, 239, 0.3);
  border-radius: 999px;
}

/* 退出登录确认（vs-dialog） */
.logout-dialog.vs-dialog-content,
.vs-dialog-content.logout-dialog {
  border-radius: 20px !important;
  border: 1px solid var(--meme-border) !important;
  background:
    radial-gradient(120% 80% at 50% -10%, color-mix(in srgb, var(--meme-primary) 16%, transparent), transparent 55%),
    var(--meme-bg-elevated) !important;
  box-shadow: var(--meme-shadow-dialog) !important;
  overflow: hidden;
}

.logout-dialog .vs-dialog__header,
.logout-dialog .vs-dialog-header {
  display: none !important;
}

.logout-dialog .vs-dialog__content,
.logout-dialog .vs-dialog-content {
  padding: 28px 24px 8px !important;
}

.logout-dialog .vs-dialog__footer,
.logout-dialog .vs-dialog-footer {
  padding: 8px 24px 24px !important;
  border-top: none !important;
  background: transparent !important;
}

.logout-dialog .vs-dialog__close,
.logout-dialog .vs-dialog-close {
  top: 12px !important;
  right: 12px !important;
  color: var(--meme-text-muted) !important;
}

.logout-dialog__body {
  text-align: center;
}

.logout-dialog__visual {
  position: relative;
  width: 76px;
  height: 76px;
  margin: 4px auto 18px;
}

.logout-dialog__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--meme-primary-soft);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--meme-primary) 8%, transparent);
  animation: logout-pulse 2.2s ease-in-out infinite;
}

.logout-dialog__icon-wrap {
  position: absolute;
  inset: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--meme-bg-elevated);
  border: 1px solid color-mix(in srgb, var(--meme-primary) 28%, var(--meme-border));
  box-shadow: 0 8px 20px var(--meme-focus-ring);
}

.logout-dialog__icon {
  font-size: 26px;
  line-height: 1;
  color: var(--meme-primary);
}

.logout-dialog__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.logout-dialog__desc {
  margin: 0 auto;
  max-width: 280px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
}

.logout-dialog__actions {
  display: flex;
  gap: 12px;
  width: 100%;
}

.logout-dialog__btn {
  flex: 1;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
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
  color: var(--meme-text);
  border: 1px solid var(--meme-border);
}

.logout-dialog__btn--ghost:not(:disabled):hover {
  background: color-mix(in srgb, var(--meme-bg-muted) 70%, var(--meme-bg-elevated));
}

.logout-dialog__btn--danger {
  color: #fff;
  background: linear-gradient(
    135deg,
    #fb7185,
    color-mix(in srgb, var(--meme-danger) 80%, #be123c)
  );
  box-shadow: 0 8px 18px color-mix(in srgb, var(--meme-danger) 35%, transparent);
}

.logout-dialog__btn--danger:not(:disabled):hover {
  filter: brightness(1.04);
  box-shadow: 0 10px 22px color-mix(in srgb, var(--meme-danger) 42%, transparent);
}

.logout-dialog__btn-spin {
  display: inline-block;
  animation: logout-spin 0.8s linear infinite;
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

@keyframes logout-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
