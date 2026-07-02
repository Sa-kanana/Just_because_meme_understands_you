<template>
  <div id="app">
    <header class="site-header">
      <div class="header-left">
        <router-link to="/" class="logo-wrap">
          <img class="logo-img" alt="网站 logo" src="@/assets/网站图标.png" />
          <span class="logo-text">只因“梗”懂你</span>
        </router-link>
        <nav class="nav-links">
          <router-link
            to="/help"
            class="nav-link"
            :class="{ active: $route.name === 'help' }"
          >
            帮助文档
          </router-link>
        </nav>
      </div>

      <div class="header-center">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索"
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
                <el-dropdown-item command="profile">
                  <i class="ri-user-3-line user-dropdown-icon"></i>
                  <span>个人主页</span>
                </el-dropdown-item>
        
                <el-dropdown-item command="account">
                  <i class="ri-settings-3-line user-dropdown-icon"></i>
                  <span>账号设置</span>
                </el-dropdown-item>
                <el-dropdown-item command="theme">
                  <i class="ri-moon-line user-dropdown-icon"></i>
                  <span>主题</span>
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
  </div>
</template>

<script>
import { Search, Bell, ArrowUp } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

export default {
  name: 'App',
  components: { Search, Bell, ArrowUp },
  data() {
    return {
      searchKeyword: '',
      isRouteLoading: false,
      progressPercent: 0,
      progressTimer: null,
      progressTweenTimer: null,
      /** 是否有未读通知（可后续对接接口） */
      hasNotification: true,
    }
  },
  computed: {
    currentUser() {
      return useAuthStore().currentUser
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
  },
  methods: {
    handleSearch() {
      const keyword = this.searchKeyword?.trim() || ''
      this.$router.push({ path: '/search', query: { keyword } })
    },
    handleUserMenuCommand(command) {
      switch (command) {
        case 'profile':
          this.goProfile()
          break
        case 'feedback':
          this.goFeedback()
          break
        case 'account':
          this.goAccountSettings()
          break
        case 'theme':
          this.openThemePanel()
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
      this.$router.push({ name: 'userProfile', params: { userId } })
    },
    goFeedback() {
      // 暂时跳到帮助文档页作为反馈入口
      this.$router.push({ path: '/help' })
    },
    goAccountSettings() {
      // 如果后续有账号设置路由，可以在这里替换为对应路径
      this.$message &&
        this.$message.info &&
        this.$message.info('账号设置功能开发中，敬请期待～')
    },
    openThemePanel() {
      // 这里预留主题切换入口，后续可接入实际主题面板
      this.$message &&
        this.$message.info &&
        this.$message.info('主题设置功能开发中，敬请期待～')
    },
    goNotifications() {
      // 可跳转通知页或打开通知列表
      this.$router.push({ path: '/' })
    },
    async handleLogout() {
      try {
        await this.$confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
      } catch {
        // 用户点了取消或关闭弹窗，直接中断退出流程
        return
      }

      await useAuthStore().logout()
      this.$router.replace({ path: '/' })
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
/* 只因「梗」懂你 - 站点主题变量 */
:root {
  --el-color-primary: #318AEF;
  --el-color-primary-light-3: #6ba8f2;
  --el-color-primary-light-5: #9ac5f6;
  --el-color-primary-light-7: #c9e1fa;
  --el-color-primary-light-9: #e8f2fd;
  --el-color-primary-dark-2: #2872d4;
  --meme-primary: #318AEF;
  --meme-primary-dark: #2872d4;
  --meme-bg: #f9fafb;
  --meme-bg-card: #ffffff;
  --meme-border: #e5e7eb;
  --meme-text: #111827;
  --meme-text-secondary: #6b7280;
  --meme-radius-sm: 6px;
  --meme-radius-md: 12px;
  --meme-radius-lg: 16px;
  --meme-radius-xl: 24px;
  --meme-shadow-card: 0 4px 12px rgba(49, 138, 239, 0.08);
  --meme-gradient-page: radial-gradient(circle at top left, #e8f2fd 0%, #f9fafb 42%, #ffffff 100%);
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  margin: 0;
  padding: 0;
}

#app {
  font-family: 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--meme-text);
  background-color: var(--meme-bg);
  min-height: 100vh;
}

/* 全局 Element 组件贴合主题 */
#app .el-button--primary {
  background-color: var(--meme-primary);
  border-color: var(--meme-primary);
  box-shadow: 0 2px 6px rgba(49, 138, 239, 0.28);
}
#app .el-button--primary:hover {
  background-color: var(--meme-primary-dark);
  border-color: var(--meme-primary-dark);
  box-shadow: 0 4px 12px rgba(49, 138, 239, 0.35);
}
#app .el-card {
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-lg);
  box-shadow: var(--meme-shadow-card);
}
#app .el-input__wrapper {
  box-shadow: 0 0 0 1px var(--meme-border) inset;
}
#app .el-input__wrapper:hover,
#app .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px var(--meme-primary) inset;
}
#app .el-badge__content.is-dot {
  background-color: #ef4444;
}

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
  background-color: rgba(49, 138, 239, 0.12);
}
.header-auth-sep {
  color: var(--meme-text-secondary, #6b7280);
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
  color: #4b5563;
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
  color: #111827;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 16px;
}

.nav-link {
  font-size: 18px;
  color: #6b7280;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 6px;
  transition: color 0.15s ease, background-color 0.15s ease, transform 0.05s ease;
}

.nav-link:hover {
  color: var(--meme-primary);
  background-color: #f3f4f6;
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
  color: #9ca3af;
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
  background-color: #f3f4f6;
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
  background-color: var(--el-color-primary-light-9, #e8f2fd);
  color: var(--meme-primary, #318AEF);
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
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  flex-shrink: 0;
  border-radius: 50%;
}
.header-avatar:hover {
  transform: scale(1.06);
  box-shadow: 0 4px 14px rgba(49, 138, 239, 0.4);
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
  background: linear-gradient(135deg, var(--meme-primary, #318AEF), var(--meme-primary-dark, #2872d4));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 6px 18px rgba(49, 138, 239, 0.35);
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.2s ease;
}

.meme-backtop-inner .el-icon {
  font-size: 18px;
}

.meme-backtop-inner:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(49, 138, 239, 0.45);
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
  color: #6b7280;
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
  border: 1px solid var(--meme-border, #e5e7eb);
  border-radius: var(--meme-radius-md, 12px);
  background-color: var(--meme-bg-card, #ffffff);
  box-shadow: var(--meme-shadow-card, 0 4px 12px rgba(49, 138, 239, 0.12));
  overflow: hidden;
}
.profile-card-popover .profile-card {
  padding: 16px 20px 20px;
  min-width: 240px;
  background: linear-gradient(to bottom, var(--el-color-primary-light-9, #e8f2fd) 0%, var(--meme-bg-card, #ffffff) 24%);
}
.profile-card-popover .profile-card-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background-color: var(--el-color-primary-light-9, #e8f2fd);
  color: var(--meme-primary, #318AEF);
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
  border-bottom: 1px solid #e5e7eb;
}
.profile-card-popover .profile-card-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--meme-primary, #318AEF), var(--meme-primary-dark, #2872d4));
  color: #fff;
  font-weight: 600;
  font-size: 22px;
}
.profile-card-popover .profile-card-info {
  min-width: 0;
}
.profile-card-popover .profile-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 4px;
  letter-spacing: 0.02em;
}
.profile-card-popover .profile-card-signature,
.profile-card-popover .profile-card-role {
  font-size: 12px;
  color: #6b7280;
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
</style>
