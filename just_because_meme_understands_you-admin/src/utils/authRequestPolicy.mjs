import { AUTH_EXPIRED_CODES } from './authSessionLifecycle.mjs'

/** 登录态请求收到未授权或明确的过期业务码时，立即结束会话。 */
export function shouldForceLogoutOnUnauthorized(response, originalConfig = {}) {
  if (!response) return false

  const status = Number(response.status)
  const code = Number(response.data && response.data.code)
  if (status !== 401 && !AUTH_EXPIRED_CODES.has(code)) return false

  const config = originalConfig || {}
  return config.__skipAuthRefresh !== true && config.__skipAuthHeader !== true
}
