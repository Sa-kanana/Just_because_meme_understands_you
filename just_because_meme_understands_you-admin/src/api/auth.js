import { request } from './request'

function assertSuccess(res, fallbackMessage) {
  if (!res || typeof res !== 'object') {
    throw new Error('接口返回异常')
  }
  if (Number(res.code) !== 1) {
    const err = new Error(res.message || fallbackMessage || '请求失败')
    err.code = res.code
    err.data = res.data
    throw err
  }
  return res.data
}

export function fetchCaptcha() {
  return request('/captcha', {
    method: 'GET',
    skipAuthRefresh: true,
    skipAuthHeader: true,
  }).then((res) => {
    const data = assertSuccess(res, '验证码获取失败') || {}
    const captchaId = data.captchaId != null ? String(data.captchaId).trim() : ''
    const imageBase64 = data.imageBase64 != null ? String(data.imageBase64).trim() : ''
    if (!captchaId || !imageBase64) {
      throw new Error('验证码返回数据不完整')
    }
    return {
      captchaId,
      imageBase64,
      expireSeconds: Number(data.expireSeconds) || 300,
    }
  })
}

export function login(payload = {}) {
  const email = payload.email != null ? String(payload.email).trim() : ''
  const password = payload.password != null ? String(payload.password) : ''
  const captchaId = payload.captchaId != null ? String(payload.captchaId).trim() : ''
  const captchaCode = payload.captchaCode != null ? String(payload.captchaCode).trim() : ''
  if (!email || !password) {
    return Promise.reject(new Error('请输入邮箱和密码'))
  }
  if (!captchaId || !captchaCode) {
    return Promise.reject(new Error('请完成人机验证'))
  }
  return request('/login', {
    method: 'POST',
    body: JSON.stringify({
      email,
      password,
      loginType: payload.loginType || 'email',
      captchaId,
      captchaCode,
    }),
    skipAuthRefresh: true,
    withCredentials: true,
  }).then((res) => {
    const data = assertSuccess(res, '登录失败') || {}
    const accessToken =
      data.access != null
        ? String(data.access).trim()
        : data.token != null
          ? String(data.token).trim()
          : data.accessToken != null
            ? String(data.accessToken).trim()
            : ''
    if (!accessToken || !data.user) {
      throw new Error('登录返回缺少 token 或用户信息')
    }
    return { token: accessToken, user: data.user }
  })
}

export function renewLogin() {
  return request('/login/refresh', {
    method: 'POST',
    body: JSON.stringify({}),
    withCredentials: true,
  }).then((res) => {
    const data = assertSuccess(res, '刷新登录态失败') || {}
    const accessToken =
      data.access != null
        ? String(data.access).trim()
        : data.token != null
          ? String(data.token).trim()
          : data.accessToken != null
            ? String(data.accessToken).trim()
            : ''
    if (!accessToken) {
      throw new Error('刷新登录态失败')
    }
    return { token: accessToken, user: data.user || null }
  })
}

export function logout() {
  return request('/logout', { method: 'POST', body: JSON.stringify({}) }).catch(() => null)
}
