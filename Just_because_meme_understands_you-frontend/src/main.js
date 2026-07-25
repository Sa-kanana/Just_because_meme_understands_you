import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import Vuesax from 'vuesax-alpha'
import { registerUiComponents } from '@/components/ui'
import { toast } from '@/utils/uiFeedback'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { setupRequestAuthLifecycle } from '@/api/request'
import { createSessionExpiredHandler } from '@/utils/authSession'
import 'vuesax-alpha/dist/index.css'
import 'vuesax-alpha/theme-chalk/dark/css-vars.css'
import '@/styles/tokens.css'
import '@/styles/base.css'
import 'vue-advanced-cropper/dist/style.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(Vuesax)
registerUiComponents(app)

const themeStore = useThemeStore(pinia)
themeStore.init()

const authStore = useAuthStore(pinia)
authStore.initFromStorage()
let validatingSession = false

const forceSessionExpiredLogout = createSessionExpiredHandler({
  authStore,
  router,
  notify: (msg) => toast.warning(msg),
})

async function validateSessionByRefresh() {
  if (!authStore.token || validatingSession) return
  validatingSession = true
  try {
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
app.mount('#app')

validateSessionByRefresh()

if (typeof window !== 'undefined') {
  window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      validateSessionByRefresh()
    }
  })
}
