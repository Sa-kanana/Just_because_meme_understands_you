import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { useAuthStore } from '@/stores/auth'
import { setupRequestAuthLifecycle } from '@/api/request'
import 'element-plus/dist/index.css'
import 'vue-advanced-cropper/dist/style.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const authStore = useAuthStore(pinia)
authStore.initFromStorage()
let validatingSession = false
const POST_LOGIN_REDIRECT_KEY = 'post_login_redirect'

function handleFinalLogout() {
  const currentFullPath = router.currentRoute.value.fullPath || '/'
  try {
    sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, currentFullPath)
  } catch (_) {
    // ignore
  }
  authStore.clearAuthState()
  router.replace({
    path: '/',
    query: {
      redirect: currentFullPath,
      reason: 'session_expired',
    },
  })
}

async function validateSessionByRefresh() {
  if (!authStore.token || validatingSession) return
  validatingSession = true
  try {
    // 主动探测 refresh 是否仍有效：失效则立刻强制登出
    await authStore.renewLogin()
  } catch (err) {
    handleFinalLogout()
  } finally {
    validatingSession = false
  }
}

setupRequestAuthLifecycle({
  getAccessToken: () => authStore.token,
  refreshAccessToken: () => authStore.renewLogin(),
  handleFinalLogout,
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
