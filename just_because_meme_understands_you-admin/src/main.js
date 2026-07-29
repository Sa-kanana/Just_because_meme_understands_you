import { createApp } from 'vue'
import ArcoVue from '@arco-design/web-vue'
import ArcoVueIcon from '@arco-design/web-vue/es/icon'
import '@arco-design/web-vue/dist/arco.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setupRequestAuthLifecycle } from './api/request'
import { useAuthStore } from './stores/auth'
import { createSessionExpiredHandler } from './utils/authSession'
import { toastError } from './utils/uiFeedback'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ArcoVue)
app.use(ArcoVueIcon)

const authStore = useAuthStore()
setupRequestAuthLifecycle({
  getAccessToken: () => authStore.token,
  refreshAccessToken: () => authStore.renewAccessToken(),
  handleFinalLogout: createSessionExpiredHandler({
    authStore,
    router,
    notify: (msg) => toastError(msg),
  }),
})

app.mount('#app')
