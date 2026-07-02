/**
 * 登录 & 注册相关接口封装
 *
 * 约定统一响应结构 DataRespose（与 Apifox 一致）：
 * { code, message, data }
 * - code === 1 表示成功，0 表示失败
 * - 其他视为失败，由前端抛出错误
 */
import { request } from './request'

const AUTH_ENDPOINTS = {
  login: '/login',
  renewLogin: '/login/refresh',
  logout: '/logout',
}

function normalizeAuthToken(rawToken) {
  const token = rawToken != null ? String(rawToken).trim() : ''
  if (!token) return ''
  return token.toLowerCase().startsWith('bearer ') ? token : `Bearer ${token}`
}

function assertDataResponseSuccess(res, fallbackMessage) {
  if (!res || typeof res !== 'object') {
    throw new Error('接口返回异常')
  }
  const code = Number(res.code)
  if (code !== 1) {
    const err = new Error(res.message || fallbackMessage || '请求失败')
    err.code = res.code
    err.data = res.data
    throw err
  }
  return res.data && typeof res.data === 'object' ? res.data : {}
}

/**
 * @typedef {Object} LoginPayload
 * @property {string} email - 邮箱，对应后端 identifier（必填）
 * @property {string} password - 密码（必填）
 * @property {string} [loginType] - 标识登录方式，对应 identity_type，如 'email'（必填，默认 'email'）
 */

/**
 * @typedef {Object} LoginUser
 * @property {string} id - 用户 id
 * @property {string} nickname - 用户名
 * @property {string} avatar - 头像
 * @property {string} signature - 个人签名
 * @property {string} role - 角色（ROLE_USER, ROLE_ADMIN）
 * @property {number} status - 0: 禁用, 1: 正常
 */

/**
 * 登录
 * 按 Apifox 接口文档：POST /login，Body 必填 email、password、loginType；
 * 响应 DataRespose，成功时 data 含 token（JWT）、user（用户信息）。
 *
 * @param {LoginPayload} payload
 * @returns {Promise<{ token: string, user: LoginUser }>}
 */
export function login(payload = {}) {
  const email = payload.email != null ? String(payload.email).trim() : ''
  const password = payload.password != null ? String(payload.password) : ''
  const loginType = payload.loginType || 'email'

  if (!email || !password) {
    return Promise.reject(new Error('请输入邮箱和密码'))
  }

  return request(AUTH_ENDPOINTS.login, {
    method: 'POST',
    body: JSON.stringify({
      email,
      password,
      loginType,
    }),
    skipAuthRefresh: true,
    withCredentials: true,
  }).then((res) => {
    const data = assertDataResponseSuccess(res, '登录失败')

    const accessToken =
      data.access != null
        ? String(data.access).trim()
        : data.token != null
          ? String(data.token).trim()
          : data.accessToken != null
            ? String(data.accessToken).trim()
            : ''

    if (!accessToken || !data.user) {
      throw new Error('登录返回缺少 access 或用户信息')
    }

    return {
      token: accessToken,
      user: data.user,
    }
  })
}

/**
 * 登录续航（自动续期）
 *
 * 默认对接接口：POST /login/renew（可通过 VUE_APP_AUTH_RENEW_ENDPOINT 覆盖）。
 * 刷新凭证通过 HttpOnly Cookie 发送，前端无需、也不能读取 refresh token。
 * 成功时返回新的 access token（并由后端在响应头写入新 refresh Cookie，实现旋转）。
 *
 * @returns {Promise<{ token: string, user: Object|null }>}
 */
export function renewLogin() {
  const renewEndpoint =
    process.env.VUE_APP_AUTH_REFRESH_ENDPOINT || AUTH_ENDPOINTS.renewLogin

  return request(renewEndpoint, {
    method: 'POST',
    skipAuthRefresh: true,
    withCredentials: true,
  }).then((res) => {
    const data = assertDataResponseSuccess(res, '登录续航失败')
    const nextToken =
      data.access != null
        ? String(data.access).trim()
        : data.token != null
          ? String(data.token).trim()
          : data.accessToken != null
            ? String(data.accessToken).trim()
            : ''
    if (!nextToken) {
      throw new Error('登录续航成功但未返回新 access token')
    }
    return {
      token: nextToken,
      user: data.user && typeof data.user === 'object' ? data.user : null,
    }
  })
}

/**
 * 退出登录
 *
 * 后端逻辑：
 * - 删除 refreshToken（Cookie）
 * - 将当前 accessToken 加入黑名单
 *
 * 前端实现：
 * - 携带 Authorization（access）
 * - 携带 withCredentials，让服务端能够清理 refresh Cookie
 *
 * @param {string} token - 当前 access token
 * @returns {Promise<{ message?: string }>}
 */
export function logout(token) {
  const authHeader = normalizeAuthToken(token)
  if (!authHeader) {
    return Promise.reject(new Error('未携带登录凭证，无法请求退出'))
  }

  return request(AUTH_ENDPOINTS.logout, {
    method: 'POST',
    headers: {
      Authorization: authHeader,
    },
    skipAuthRefresh: true,
    withCredentials: true,
  }).then((res) => {
    assertDataResponseSuccess(res, '退出失败')
    return { message: res.message || '退出成功' }
  })
}

/**
 * 发送注册验证码
 *
 * 严格按照 Apifox 接口文档：
 * - URL: POST /register/send-code
 * - Content-Type: application/json
 * - Body: { email }
 * - 响应：{ code, message, data: { retryAfter } }
 *
 * @param {string} email
 * @returns {Promise<{ retryAfter: number, message: string }>}
 */
export function sendRegisterCode(email) {
  const normalizedEmail = email != null ? String(email).trim() : ''

  if (!normalizedEmail) {
    return Promise.reject(new Error('请输入邮箱'))
  }

  return request('/register/send-code', {
    method: 'POST',
    body: JSON.stringify({
      email: normalizedEmail,
    }),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('发送验证码接口返回异常')
    }

    const code = Number(res.code)
    const message =
      res.message || '验证码发送失败，请稍后重试'
    const data = res.data || {}

    if (code !== 1) {
      const err = new Error(message)
      err.code = res.code
      err.data = data
      throw err
    }

    const retryAfter = Number(
      data.retryAfter != null ? data.retryAfter : 60
    )

    return {
      retryAfter: Number.isFinite(retryAfter) ? retryAfter : 60,
      message,
    }
  })
}

/**
 * 注册
 *
 * 严格按照 Apifox 接口文档：
 * - URL: POST /register
 * - Content-Type: application/json
 * - Body: { email, password, confirmPassword, verificationCode, nickname }
 * - 响应：{ code, message, data: { userId, email, nickname, createdAt } }
 *
 * 说明：
 * - 后端只存一个密码，confirmPassword 仅前端做一致性校验，但仍按文档原样发送。
 *
 * @param {Object} payload
 * @param {string} payload.email
 * @param {string} payload.password
 * @param {string} payload.confirmPassword
 * @param {string} payload.verificationCode
 * @param {string} [payload.nickname] - 用户名，不传则默认取邮箱前缀
 * @returns {Promise<{ userId: number|string, email: string, nickname: string, createdAt: string, message: string }>}
 */
export function register(payload = {}) {
  const email = payload.email != null ? String(payload.email).trim() : ''
  const password = payload.password != null ? String(payload.password) : ''
  const confirmPassword =
    payload.confirmPassword != null
      ? String(payload.confirmPassword)
      : ''
  const verificationCode =
    payload.verificationCode != null
      ? String(payload.verificationCode).trim()
      : ''
  const nickname =
    payload.nickname != null ? String(payload.nickname).trim() : ''

  if (!email) {
    return Promise.reject(new Error('请输入邮箱'))
  }
  if (!password || !confirmPassword) {
    return Promise.reject(new Error('请输入密码并确认密码'))
  }
  if (password !== confirmPassword) {
    return Promise.reject(new Error('两次输入的密码不一致'))
  }
  if (!verificationCode) {
    return Promise.reject(new Error('请输入验证码'))
  }

  const finalNickname =
    nickname ||
    (email.includes('@') ? email.split('@')[0] : '') ||
    '梗友'

  return request('/register', {
    method: 'POST',
    body: JSON.stringify({
      email,
      password,
      confirmPassword,
      verificationCode,
      nickname: finalNickname,
    }),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('注册接口返回异常')
    }

    const code = Number(res.code)
    const message = res.message || '注册失败'
    const data = res.data || {}

    if (code !== 1) {
      const err = new Error(message)
      err.code = res.code
      err.data = data
      throw err
    }

    return {
      userId: data.userId,
      email: data.email,
      nickname: data.nickname,
      createdAt: data.createdAt,
      message,
    }
  })
}

// ========== 忘记密码 / 重置密码（三步流程）==========

/**
 * 第一步：请求重置（发邮件）
 * 后端验证邮箱是否存在，Redis 存入 forgot_password:email -> code, 5min。
 * 约定：POST /password/reset/request，Body: { email }，响应 DataRespose，成功时 data 可有 retryAfter(秒)。
 *
 * @param {string} email
 * @returns {Promise<{ retryAfter?: number, message: string }>}
 */
export function requestPasswordReset(email) {
  const normalizedEmail = email != null ? String(email).trim() : ''
  if (!normalizedEmail) {
    return Promise.reject(new Error('请输入邮箱'))
  }
  return request('/password/reset/request', {
    method: 'POST',
    body: JSON.stringify({ email: normalizedEmail }),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('请求重置接口返回异常')
    }
    const code = Number(res.code)
    const message = res.message || '验证码发送失败，请稍后重试'
    const data = res.data || {}
    if (code !== 1) {
      const err = new Error(message)
      err.code = res.code
      err.data = data
      throw err
    }
    const retryAfter = data.retryAfter != null ? Number(data.retryAfter) : 60
    return {
      retryAfter: Number.isFinite(retryAfter) ? retryAfter : 60,
      message,
    }
  })
}

/**
 * 第二步：验证验证码并获取重置令牌
 * 后端核验通过后生成临时 token 存 Redis（reset_token -> userId, 10min），并返回 token。
 * 约定：POST /password/reset/verify，Body: { email, code }，响应 DataRespose，成功时 data: { token }。
 *
 * @param {Object} payload
 * @param {string} payload.email
 * @param {string} payload.code - 邮箱收到的验证码
 * @returns {Promise<{ token: string }>}
 */
export function verifyResetCode(payload = {}) {
  const email = payload.email != null ? String(payload.email).trim() : ''
  const code = payload.code != null ? String(payload.code).trim() : ''
  if (!email) {
    return Promise.reject(new Error('请输入邮箱'))
  }
  if (!code) {
    return Promise.reject(new Error('请输入验证码'))
  }
  return request('/password/reset/verify', {
    method: 'POST',
    body: JSON.stringify({ email, code }),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('验证验证码接口返回异常')
    }
    const resCode = Number(res.code)
    const message = res.message || '验证码错误或已过期'
    const data = res.data || {}
    if (resCode !== 1) {
      const err = new Error(message)
      err.code = res.code
      err.data = data
      throw err
    }
    if (!data.token) {
      throw new Error('未返回重置令牌，请重试')
    }
    return { token: data.token }
  })
}

/**
 * 第三步：执行重置（提交新密码）
 * 前端提交 token + newPassword；后端用 token 从 Redis 取 userId，更新 user_auth 密码（BCrypt），并使该用户 JWT 失效。
 * 接口定义：POST /password/reset/confirm，Body: { token, newPassword }，响应 DataRespose。
 *
 * @param {Object} payload
 * @param {string} payload.token - 第二步返回的重置令牌
 * @param {string} payload.newPassword - 新密码
 * @returns {Promise<{ message?: string }>}
 */
export function confirmPasswordReset(payload = {}) {
  const token = payload.token != null ? String(payload.token).trim() : ''
  const newPassword = payload.newPassword != null ? String(payload.newPassword) : ''
  if (!token) {
    return Promise.reject(new Error('重置令牌缺失，请重新走一遍验证流程'))
  }
  if (!newPassword) {
    return Promise.reject(new Error('请输入新密码'))
  }
  return request('/password/reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('重置密码接口返回异常')
    }
    const resCode = Number(res.code)
    const message = res.message || '重置失败，请重试'
    const data = res.data || {}
    if (resCode !== 1) {
      const err = new Error(message)
      err.code = res.code
      err.data = data
      throw err
    }
    return { message: res.message || '密码已重置，请使用新密码登录' }
  })
}


