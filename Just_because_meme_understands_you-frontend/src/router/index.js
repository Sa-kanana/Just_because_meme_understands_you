import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBreadcrumbStore } from '@/stores/breadcrumb'

const GUEST_AUTH_PATHS = new Set(['/login', '/register', '/forgot-password'])
const guestOnlyMeta = { guestOnly: true }

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: { hideBreadcrumb: true },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/Search.vue'),
  },
  {
    path: '/detail/:id',
    name: 'memeDetail',
    component: () => import('@/views/MemeDetail.vue'),
    props: true,
  },
  {
    path: '/help',
    name: 'help',
    component: () => import('@/views/HelpDocs.vue'),
  },
  {
    path: '/publish',
    name: 'publishMeme',
    component: () => import('@/views/PublishMeme.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: '发布梗' },
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: '消息中心' },
  },
  {
    path: '/settings',
    name: 'accountSettings',
    component: () => import('@/views/AccountSettings.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: '账号设置' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { ...guestOnlyMeta, breadcrumbLabel: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { ...guestOnlyMeta, breadcrumbLabel: '注册' },
  },
  {
    path: '/forgot-password',
    name: 'forgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { ...guestOnlyMeta, breadcrumbLabel: '找回密码' },
  },
  {
    path: '/user/me',
    redirect: (to) => ({
      name: 'userProfile',
      params: { userId: 'me' },
      query: to.query,
    }),
  },
  {
    path: '/user/:userId',
    name: 'userProfile',
    component: () => import('@/views/UserProfile.vue'),
    props: true,
  },
  {
    path: '/error/:code?',
    name: 'error',
    component: () => import('@/views/ErrorPage.vue'),
    props: (route) => ({
      code: route.params.code || 500,
      message: route.query.message || '',
    }),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/ErrorPage.vue'),
    props: {
      code: 404,
    },
    meta: { hideBreadcrumb: true },
  },
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
})

function resolveSafeRedirectPath(rawPath) {
  const path = rawPath != null ? String(rawPath).trim() : ''
  if (!path || !path.startsWith('/') || path.startsWith('//')) return '/'
  if (GUEST_AUTH_PATHS.has(path)) return '/'
  return path
}

function dispatchRouteLoading(loading) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent('route-loading', { detail: { loading } })
    )
  }
}

router.beforeEach((to, from, next) => {
  const routeIdentity = (r) => `${String(r.name || '')}::${JSON.stringify(r.params || {})}`
  if (routeIdentity(to) !== routeIdentity(from)) {
    try {
      useBreadcrumbStore().clearPatch()
    } catch (_) {
      // Pinia 尚未挂载时忽略
    }
  }
  const authStore = useAuthStore()
  if (to.meta?.requiresAuth && !authStore.isLoggedIn) {
    next({
      name: 'login',
      query: { redirect: resolveSafeRedirectPath(to.fullPath) },
    })
    return
  }
  if (to.meta?.guestOnly && authStore.isLoggedIn) {
    next(resolveSafeRedirectPath(to.query.redirect))
    return
  }
  dispatchRouteLoading(true)
  next()
})

router.afterEach(() => {
  dispatchRouteLoading(false)
})

export default router
