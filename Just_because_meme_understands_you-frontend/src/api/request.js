import axios from 'axios'
import {
  isAuthExpiredError,
  markAuthErrorHandled,
  isAuthErrorHandled,
} from '@/utils/authSession'

const BASE_URL = process.env.VUE_APP_API_BASE_URL || '/api'
const DEFAULT_TIMEOUT = Number(process.env.VUE_APP_API_TIMEOUT || 15000)

const http = axios.create({
  baseURL: BASE_URL,
  timeout: Number.isFinite(DEFAULT_TIMEOUT) ? DEFAULT_TIMEOUT : 15000,
  withCredentials: true,
})

const authLifecycle = {
  getAccessToken: null,
  refreshAccessToken: null,
  handleFinalLogout: null,
}

let isRefreshing = false
const requestsQueue = []

function withBearerToken(token) {
  const raw = token != null ? String(token).trim() : ''
  if (!raw) return ''
  return raw.toLowerCase().startsWith('bearer ') ? raw : `Bearer ${raw}`
}

function parseRequestBody(body) {
  if (body == null || body === '') return undefined
  if (typeof body !== 'string') return body
  try {
    return JSON.parse(body)
  } catch {
    return body
  }
}

function shouldAttemptTokenRefresh(originalConfig, response) {
  if (!response || response.status !== 401) return false
  const cfg = originalConfig || {}
  if (cfg.__skipAuthRefresh === true) return false
  if (cfg.__isRetryAfterRefresh === true) return false
  return typeof authLifecycle.refreshAccessToken === 'function'
}

function shouldForceLogoutOn401(originalConfig, response) {
  if (!response || response.status !== 401) return false
  const cfg = originalConfig || {}
  if (cfg.__isRetryAfterRefresh === true) return true
  // 公开鉴权接口（验证码/登录等）：401 只表示业务失败，不清本地会话
  if (cfg.__skipAuthRefresh === true || cfg.__skipAuthHeader === true) {
    return false
  }
  return false
}

function rejectBusinessError(response, payload) {
  const resData = payload != null && typeof payload === 'object' ? payload : {}
  const message = resData.message || resData.msg || `请求失败: ${response.status}`
  const err = new Error(message)
  err.code = resData.code != null ? Number(resData.code) : undefined
  err.data = resData
  err.status = response.status
  if (
    isAuthExpiredError(err) &&
    typeof authLifecycle.handleFinalLogout === 'function'
  ) {
    authLifecycle.handleFinalLogout(err)
  }
  return Promise.reject(markAuthErrorHandled(err))
}

function normalizeResponse(response) {
  const resData = response.data
  const contentType =
    response.headers && response.headers['content-type']
      ? String(response.headers['content-type'])
      : ''

  if (contentType.includes('application/json')) {
    return resData != null && typeof resData === 'object' ? resData : {}
  }
  const text =
    typeof resData === 'string'
      ? resData
      : resData != null
        ? String(resData)
        : ''
  return text ? { data: text } : {}
}

http.interceptors.request.use((config) => {
  const cfg = config || {}
  const headers = cfg.headers || {}

  // 公开接口（登录/验证码等）不附带旧 token，避免误触 401 清会话
  if (cfg.__skipAuthHeader === true) {
    delete headers.Authorization
    delete headers.authorization
    cfg.headers = headers
    return cfg
  }

  // 已显式传入 Authorization 时不覆盖，兼容登录/退出等特殊请求
  if (!headers.Authorization && !headers.authorization && typeof authLifecycle.getAccessToken === 'function') {
    const token = withBearerToken(authLifecycle.getAccessToken())
    if (token) {
      headers.Authorization = token
    }
  }
  cfg.headers = headers
  return cfg
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const response = error && error.response
    const originalConfig = error && error.config ? error.config : null

    if (!response || !originalConfig) {
      return Promise.reject(error)
    }

    const canRefresh = shouldAttemptTokenRefresh(originalConfig, response)

    if (canRefresh) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          requestsQueue.push({ resolve, reject })
        }).then((nextToken) => {
          const bearerToken = withBearerToken(nextToken)
          if (!bearerToken) {
            throw new Error('登录态已过期，请重新登录')
          }
          originalConfig.__isRetryAfterRefresh = true
          originalConfig.headers = originalConfig.headers || {}
          originalConfig.headers.Authorization = bearerToken
          return http.request(originalConfig)
        })
      }

      isRefreshing = true
      try {
        const nextToken = await Promise.resolve(authLifecycle.refreshAccessToken())
        const bearerToken = withBearerToken(nextToken)
        if (!bearerToken) {
          throw new Error('登录态已过期，请重新登录')
        }
        while (requestsQueue.length > 0) {
          const pending = requestsQueue.shift()
          pending && pending.resolve && pending.resolve(nextToken)
        }
        originalConfig.__isRetryAfterRefresh = true
        originalConfig.headers = originalConfig.headers || {}
        originalConfig.headers.Authorization = bearerToken
        return http.request(originalConfig)
      } catch (refreshError) {
        while (requestsQueue.length > 0) {
          const pending = requestsQueue.shift()
          pending && pending.reject && pending.reject(refreshError)
        }
        if (typeof authLifecycle.handleFinalLogout === 'function') {
          authLifecycle.handleFinalLogout(refreshError)
        }
        return Promise.reject(markAuthErrorHandled(refreshError))
      } finally {
        isRefreshing = false
      }
    }

    if (shouldForceLogoutOn401(originalConfig, response)) {
      if (typeof authLifecycle.handleFinalLogout === 'function') {
        authLifecycle.handleFinalLogout(error)
      }
      const data = response.data
      const message =
        (data && (data.message || data.msg)) || '登录已过期，请重新登录'
      const e = new Error(message)
      e.code = data && data.code
      e.data = data
      e.status = response.status
      return Promise.reject(markAuthErrorHandled(e))
    }

    return Promise.reject(error)
  }
)

export function setupRequestAuthLifecycle({
  getAccessToken,
  refreshAccessToken,
  handleFinalLogout,
} = {}) {
  authLifecycle.getAccessToken =
    typeof getAccessToken === 'function' ? getAccessToken : null
  authLifecycle.refreshAccessToken =
    typeof refreshAccessToken === 'function' ? refreshAccessToken : null
  authLifecycle.handleFinalLogout =
    typeof handleFinalLogout === 'function' ? handleFinalLogout : null
}

export async function request(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const isGetLike = method === 'GET' || method === 'HEAD'
  const isFormDataBody =
    typeof FormData !== 'undefined' && options.body instanceof FormData

  const headers = isGetLike || isFormDataBody
    ? { ...(options.headers || {}) }
    : { 'Content-Type': 'application/json', ...(options.headers || {}) }

  try {
    const response = await http.request({
      url,
      method,
      headers,
      data: parseRequestBody(options.body),
      timeout: options.timeout,
      withCredentials:
        typeof options.withCredentials === 'boolean'
          ? options.withCredentials
          : true,
      __skipAuthRefresh: options.skipAuthRefresh === true,
      __skipAuthHeader: options.skipAuthHeader === true,
    })
    const payload = normalizeResponse(response)
    if (
      payload &&
      typeof payload === 'object' &&
      payload.code != null &&
      Number(payload.code) !== 1
    ) {
      return rejectBusinessError(response, payload)
    }
    return payload
  } catch (err) {
    if (isAuthErrorHandled(err)) {
      throw err
    }
    if (err && err.response) {
      const data = err.response.data
      const message =
        (data && (data.message || data.msg)) || `请求失败: ${err.response.status}`
      const e = new Error(message)
      e.code = data && data.code != null ? Number(data.code) : undefined
      e.data = data
      e.status = err.response.status
      if (
        isAuthExpiredError(e) &&
        typeof authLifecycle.handleFinalLogout === 'function' &&
        shouldForceLogoutOn401(err.config, err.response)
      ) {
        authLifecycle.handleFinalLogout(e)
        throw markAuthErrorHandled(e)
      }
      throw e
    }
    throw err
  }
}

/**
 * SSE / 流式请求（需 ReadableStream，走 fetch；鉴权与 401 refresh 与 request 对齐）
 * @returns {Promise<Response>}
 */
export async function streamRequest(url, options = {}, { retriedAfterRefresh = false } = {}) {
  const method = (options.method || 'POST').toUpperCase()
  const headers = {
    Accept: 'text/event-stream',
    ...(options.headers || {}),
  }
  if (
    method !== 'GET' &&
    method !== 'HEAD' &&
    !(typeof FormData !== 'undefined' && options.body instanceof FormData) &&
    !headers['Content-Type'] &&
    !headers['content-type']
  ) {
    headers['Content-Type'] = 'application/json'
  }
  if (!headers.Authorization && !headers.authorization && typeof authLifecycle.getAccessToken === 'function') {
    const token = withBearerToken(authLifecycle.getAccessToken())
    if (token) headers.Authorization = token
  }

  const absoluteUrl = /^https?:\/\//i.test(url) ? url : `${BASE_URL}${url.startsWith('/') ? url : `/${url}`}`
  const response = await fetch(absoluteUrl, {
    method,
    headers,
    body: options.body,
    credentials:
      typeof options.withCredentials === 'boolean'
        ? options.withCredentials
          ? 'include'
          : 'omit'
        : 'include',
    signal: options.signal,
  })

  if (response.status !== 401) {
    return response
  }

  const canRefresh =
    !retriedAfterRefresh &&
    options.skipAuthRefresh !== true &&
    typeof authLifecycle.refreshAccessToken === 'function'

  if (canRefresh) {
    try {
      await Promise.resolve(authLifecycle.refreshAccessToken())
      return streamRequest(url, options, { retriedAfterRefresh: true })
    } catch (refreshError) {
      if (typeof authLifecycle.handleFinalLogout === 'function') {
        authLifecycle.handleFinalLogout(refreshError)
      }
      throw markAuthErrorHandled(refreshError)
    }
  }

  let payload = null
  try {
    payload = await response.clone().json()
  } catch {
    // ignore
  }
  const message =
    (payload && (payload.message || payload.msg)) || '登录已过期，请重新登录'
  const err = new Error(message)
  err.code = payload && payload.code != null ? Number(payload.code) : 401
  err.status = 401
  err.data = payload
  if (typeof authLifecycle.handleFinalLogout === 'function') {
    authLifecycle.handleFinalLogout(err)
  }
  throw markAuthErrorHandled(err)
}
