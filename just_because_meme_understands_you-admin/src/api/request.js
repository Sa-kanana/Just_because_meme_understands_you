import axios from 'axios'
import {
  isAuthExpiredError,
  markAuthErrorHandled,
  isAuthErrorHandled,
} from '@/utils/authSession'
import { shouldForceLogoutOnUnauthorized } from '@/utils/authRequestPolicy.mjs'

const configuredBaseUrl = process.env.VUE_APP_API_BASE_URL || '/api'
const normalizedBaseUrl = configuredBaseUrl.replace(/\/+$/, '')
const apiRoot = normalizedBaseUrl.endsWith('/v1')
  ? normalizedBaseUrl.slice(0, -3)
  : normalizedBaseUrl
const USER_API_BASE_URL = `${apiRoot}/v1`
const BASE_URL = `${apiRoot}/admin/v1`
const DEFAULT_TIMEOUT = Number(process.env.VUE_APP_API_TIMEOUT || 15000)

const http = axios.create({
  baseURL: BASE_URL,
  timeout: Number.isFinite(DEFAULT_TIMEOUT) ? DEFAULT_TIMEOUT : 15000,
  withCredentials: true,
})

const authLifecycle = {
  getAccessToken: null,
  handleFinalLogout: null,
}

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

function rejectBusinessError(response, payload) {
  const resData = payload != null && typeof payload === 'object' ? payload : {}
  const message = resData.message || resData.msg || `请求失败: ${response.status}`
  const err = new Error(message)
  err.code = resData.code != null ? Number(resData.code) : undefined
  err.data = resData
  err.status = response.status
  if (
    isAuthExpiredError(err) &&
    shouldForceLogoutOnUnauthorized(response, response.config) &&
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
  (error) => {
    const response = error && error.response
    const originalConfig = error && error.config ? error.config : null
    if (!response || !originalConfig) {
      return Promise.reject(error)
    }

    if (shouldForceLogoutOnUnauthorized(response, originalConfig)) {
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
  handleFinalLogout,
} = {}) {
  authLifecycle.getAccessToken =
    typeof getAccessToken === 'function' ? getAccessToken : null
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
    const requestUrl = options.apiSurface === 'user'
      ? url
      : String(url).replace(/^\/admin(?=\/)/, '')
    const axiosConfig = {
      url: requestUrl,
      method,
      headers,
      data: parseRequestBody(options.body),
      baseURL: options.apiSurface === 'user' ? USER_API_BASE_URL : BASE_URL,
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
        shouldForceLogoutOnUnauthorized(err.response, err.config)
      ) {
        authLifecycle.handleFinalLogout(e)
        throw markAuthErrorHandled(e)
      }
      throw e
    }
    throw err
  }
}
