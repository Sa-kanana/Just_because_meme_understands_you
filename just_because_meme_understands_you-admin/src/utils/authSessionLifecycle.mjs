/** 登录会话过期统一处理。 */
export const POST_LOGIN_REDIRECT_KEY = 'admin_post_login_redirect'
export const AUTH_EXPIRED_CODES = new Set([401, 1002])

const GUEST_AUTH_PATHS = new Set(['/login'])

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

/** 新会话建立后允许未来的过期事件再次提示。 */
export function resetSessionExpiredHandler() {
  sessionLogoutInProgress = false
}

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
    const currentFullPath = options.redirectPath || currentRoute?.fullPath || '/'
    const safeRedirect = resolveSafeRedirectPath(currentFullPath)

    try {
      sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, safeRedirect)
    } catch (_) {
      // 忽略浏览器禁用或限制 sessionStorage 的情况。
    }

    if (authStore && typeof authStore.clearAuthState === 'function') {
      authStore.clearAuthState()
    }

    const isLoginRoute = currentRoute?.name === 'login'
    if (!options.silent && !isLoginRoute && typeof notify === 'function') {
      notify('登录已过期，请重新登录')
    }

    if (router && typeof router.replace === 'function') {
      router.replace({
        name: 'login',
        query: { reason: 'expired', redirect: safeRedirect },
      }).catch(() => {})
    }
  }
}
