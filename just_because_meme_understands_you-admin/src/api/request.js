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
  return resData != null && typeof resData === 'object' ? resData : {}
}

http.interceptors.request.use((config) => {
  const cfg = config || {}
  const headers = cfg.headers || {}
  if (cfg.__skipAuthHeader === true) {
    delete headers.Authorization
    delete headers.authorization
    cfg.headers = headers
    return cfg
  }
  if (!headers.Authorization && !headers.authorization && typeof authLifecycle.getAccessToken === 'function') {
    const token = withBearerToken(authLifecycle.getAccessToken())
    if (token) headers.Authorization = token
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

    if (shouldAttemptTokenRefresh(originalConfig, response)) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          requestsQueue.push({ resolve, reject })
        }).then((nextToken) => {
          const bearerToken = withBearerToken(nextToken)
          if (!bearerToken) throw new Error('登录态已过期，请重新登录')
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
        if (!bearerToken) throw new Error('登录态已过期，请重新登录')
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
      const message = (data && (data.message || data.msg)) || '登录已过期，请重新登录'
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

  const headers =
    isGetLike || isFormDataBody
      ? { ...(options.headers || {}) }
      : { 'Content-Type': 'application/json', ...(options.headers || {}) }

  try {
    const axiosConfig = {
      url,
      method,
      headers,
      data: parseRequestBody(options.body),
      withCredentials:
        typeof options.withCredentials === 'boolean'
          ? options.withCredentials
          : true,
      __skipAuthRefresh: options.skipAuthRefresh === true,
      __skipAuthHeader: options.skipAuthHeader === true,
    }
    if (options.timeout != null && Number.isFinite(Number(options.timeout))) {
      axiosConfig.timeout = Number(options.timeout)
    }
    const response = await http.request(axiosConfig)
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
    if (isAuthErrorHandled(err)) throw err
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
