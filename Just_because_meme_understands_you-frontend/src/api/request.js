import axios from 'axios'

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

    const shouldSkipRefresh =
      originalConfig.__skipAuthRefresh === true ||
      originalConfig.__isRetryAfterRefresh === true

    const canRefresh =
      response.status === 401 &&
      !shouldSkipRefresh &&
      typeof authLifecycle.refreshAccessToken === 'function'

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
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
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
    })
    return normalizeResponse(response)
  } catch (err) {
    if (err && err.response) {
      const data = err.response.data
      const message =
        (data && (data.message || data.msg)) || `请求失败: ${err.response.status}`
      const e = new Error(message)
      e.code = data && data.code
      e.data = data
      e.status = err.response.status
      throw e
    }
    throw err
  }
}
