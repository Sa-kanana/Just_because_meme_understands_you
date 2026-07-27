/**
 * 登录会话过期统一处理（企业级）
 * - 识别 401 / refresh 失效（1002）
 * - 幂等清理本地态 + 跳转登录 + 回跳路径
 * - 避免多处重复弹窗 / 重复跳转
 */

export const POST_LOGIN_REDIRECT_KEY = 'post_login_redirect'

/** 与后端 Result.CODE_* 对齐 */
export const AUTH_EXPIRED_CODES = new Set([401, 1002])

const GUEST_AUTH_PATHS = new Set([
  '/login',
  '/register',
  '/forgot-password',
  '/login/oauth/callback',
])

let sessionLogoutInProgress = false

export function resolveSafeRedirectPath(rawPath) {
  const path = rawPath != null ? String(rawPath).trim() : ''
  if (!path || !path.startsWith('/') || path.startsWith('//')) return '/'
  if (GUEST_AUTH_PATHS.has(path)) return '/'
  return path
}

export function isAuthExpiredError(error) {
  if (!error || typeof error !== 'object') return false
  const status = Number(error.status)
  const code = Number(error.code)
  return status === 401 || AUTH_EXPIRED_CODES.has(code)
}

export function markAuthErrorHandled(error) {
  if (error && typeof error === 'object') {
    error.__authHandled = true
  }
  return error
}

export function isAuthErrorHandled(error) {
  return !!(error && error.__authHandled)
}

/**
 * @param {{ authStore: object, router: object, notify?: (msg: string) => void }} deps
 */
export function createSessionExpiredHandler(deps) {
  const { authStore, router, notify } = deps || {}

  return function forceSessionExpiredLogout(error, options = {}) {
    if (sessionLogoutInProgress) {
      markAuthErrorHandled(error)
      return
    }
    sessionLogoutInProgress = true
    markAuthErrorHandled(error)

    const currentRoute = router?.currentRoute?.value
    const currentFullPath =
      options.redirectPath || currentRoute?.fullPath || '/'
    const safeRedirect = resolveSafeRedirectPath(currentFullPath)

    try {
      sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, safeRedirect)
    } catch (_) {
      // ignore
    }

    if (authStore && typeof authStore.clearAuthState === 'function') {
      authStore.clearAuthState()
    }

    const isLoginRoute = currentRoute?.name === 'login'
    if (!options.silent && !isLoginRoute && typeof notify === 'function') {
      notify('登录已过期，请重新登录')
    }

    if (!isLoginRoute && router) {
      router.replace({
        name: 'login',
        query: {
          redirect: safeRedirect,
          reason: 'session_expired',
        },
      })
    }

    window.setTimeout(() => {
      sessionLogoutInProgress = false
    }, 800)
  }
}
