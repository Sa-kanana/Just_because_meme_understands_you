import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const GUEST_AUTH_PATHS = new Set(['/login', '/register', '/forgot-password'])
const guestOnlyMeta = { guestOnly: true }

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
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
    meta: { requiresAuth: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: guestOnlyMeta,
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: guestOnlyMeta,
  },
  {
    path: '/forgot-password',
    name: 'forgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: guestOnlyMeta,
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
  /**
   * 通用异常页面
   * /error/:code?message=... ，例如：
   * - /error/404
   * - /error/403?message=当前账号无权限
   * - /error/biz?message=当前梗图已下架
   */
  {
    path: '/error/:code?',
    name: 'error',
    component: () => import('@/views/ErrorPage.vue'),
    props: (route) => ({
      code: route.params.code || 500,
      message: route.query.message || '',
    }),
  },
  /**
   * 兜底 404：未匹配到的路径统一落到 404 页面
   */
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/ErrorPage.vue'),
    props: {
      code: 404,
    },
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

router.beforeEach((to, _from, next) => {
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
