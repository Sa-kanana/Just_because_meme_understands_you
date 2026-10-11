import { createApp } from 'vue'
import ArcoVue, { Modal } from '@arco-design/web-vue'
import ArcoVueIcon from '@arco-design/web-vue/es/icon'
import '@arco-design/web-vue/dist/arco.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setupRequestAuthLifecycle } from './api/request'
import { useAuthStore } from './stores/auth'
import { createSessionExpiredHandler } from './utils/authSession'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ArcoVue)
app.use(ArcoVueIcon)

const authStore = useAuthStore()
setupRequestAuthLifecycle({
  getAccessToken: () => authStore.token,
  handleFinalLogout: createSessionExpiredHandler({
    authStore,
    router,
    notify: (message) => Modal.warning({
      title: '登录状态已过期',
      content: message,
      okText: '重新登录',
      hideCancel: true,
      closable: false,
      maskClosable: false,
      escToClose: false,
    }, app._context),
  }),
})

app.mount('#app')
