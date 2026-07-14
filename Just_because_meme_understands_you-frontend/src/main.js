import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { setupRequestAuthLifecycle } from '@/api/request'
import { createSessionExpiredHandler } from '@/utils/authSession'
import 'element-plus/dist/index.css'
/* Element Plus 官方暗色：仅当 html 带 .dark 时生效 */
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/tokens.css'
import '@/styles/base.css'
import 'vue-advanced-cropper/dist/style.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const themeStore = useThemeStore(pinia)
themeStore.init()

const authStore = useAuthStore(pinia)
authStore.initFromStorage()
let validatingSession = false

const forceSessionExpiredLogout = createSessionExpiredHandler({
  authStore,
  router,
  notify: (msg) => ElMessage.warning(msg),
})

async function validateSessionByRefresh() {
  if (!authStore.token || validatingSession) return
  validatingSession = true
  try {
    // 主动探测 refresh 是否仍有效：失效则立刻强制登出
    await authStore.renewLogin()
  } catch (_) {
    forceSessionExpiredLogout(null)
  } finally {
    validatingSession = false
  }
}

setupRequestAuthLifecycle({
  getAccessToken: () => authStore.token,
  refreshAccessToken: () => authStore.renewLogin(),
  handleFinalLogout: forceSessionExpiredLogout,
})

app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

// 启动时做一次会话校验：若 refresh 在 Redis 已失效，立即退出登录
validateSessionByRefresh()

// 页面回到前台时再次校验，避免长时间挂起导致登录态显示滞后
if (typeof window !== 'undefined') {
  window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      validateSessionByRefresh()
    }
  })
}
