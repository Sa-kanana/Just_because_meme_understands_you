import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import { resolveRouteBreadcrumbs } from '@/utils/pageBreadcrumb'

const GUEST_AUTH_PATHS = new Set(['/login', '/register', '/forgot-password', '/login/oauth/callback'])
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
    path: '/trends',
    name: 'trends',
    component: () => import('@/views/TrendPage.vue'),
    meta: { breadcrumbLabel: '热梗趋势' },
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
    path: '/feedback',
    name: 'feedback',
    component: () => import('@/views/Feedback.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: '提交反馈' },
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
    path: '/ai',
    name: 'aiSearch',
    component: () => import('@/views/AiSearch.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: 'AI 搜梗' },
  },
  {
    path: '/settings',
    name: 'accountSettings',
    component: () => import('@/views/AccountSettings.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: '账号设置' },
  },
  {
    path: '/settings/community-safety',
    name: 'userSafetySettings',
    component: () => import('@/views/UserSafetySettings.vue'),
    meta: { requiresAuth: true, breadcrumbLabel: '社区安全' },
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
    path: '/login/oauth/callback',
    name: 'oauthCallback',
    component: () => import('@/views/OauthCallback.vue'),
    meta: { ...guestOnlyMeta, hideBreadcrumb: true },
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
  try {
    const breadcrumbStore = useBreadcrumbStore()
    if (from.name) {
      const currentItems = resolveRouteBreadcrumbs(from, {
        authStore: useAuthStore(),
        patch: breadcrumbStore.patch,
        trail: breadcrumbStore.trail,
      })
      breadcrumbStore.updateCurrentVisit(from, currentItems.at(-1))
    }
    if (routeIdentity(to) !== routeIdentity(from)) {
      breadcrumbStore.clearPatch()
    }
  } catch (_) {
    // Pinia 尚未挂载时忽略面包屑状态更新
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

router.afterEach((to, _from, failure) => {
  dispatchRouteLoading(false)
  if (failure) return
  try {
    const breadcrumbStore = useBreadcrumbStore()
    const currentItems = resolveRouteBreadcrumbs(to, {
      authStore: useAuthStore(),
      patch: breadcrumbStore.patch,
    })
    breadcrumbStore.recordVisit(to, currentItems.at(-1))
  } catch (_) {
    // Pinia 尚未挂载时忽略面包屑状态更新
  }
})

export default router
