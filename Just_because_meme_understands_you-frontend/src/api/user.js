import { request } from './request'

function withAuthHeader(token) {
  const raw = token != null ? String(token).trim() : ''
  if (!raw) return {}
  return {
    Authorization: raw.toLowerCase().startsWith('bearer ') ? raw : `Bearer ${raw}`,
  }
}

function pickNumber(...values) {
  for (const value of values) {
    if (value == null || value === '') continue
    const num = Number(value)
    if (Number.isFinite(num)) return num
  }
  return 0
}

function normalizeStats(raw) {
  const src = raw && typeof raw === 'object' ? raw : {}
  return {
    memeCount: pickNumber(src.memeCount, src.meme_count),
    likeReceived: pickNumber(src.likeReceived, src.like_received),
    favoriteCount: pickNumber(src.favoriteCount, src.favorite_count),
    followCount: pickNumber(src.followCount, src.follow_count),
    fansCount: pickNumber(src.fansCount, src.fans_count, src.fansount),
  }
}

export function getUserProfile(userId, token) {
  const id = userId != null ? String(userId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少用户ID'))
  }
  if (!/^\d+$/.test(id)) {
    return Promise.reject(new Error('用户ID格式不正确'))
  }

  return request(`/user/${encodeURIComponent(id)}/profile`, {
    method: 'GET',
    headers: withAuthHeader(token),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('个人主页接口返回异常')
    }
    const code = Number(res.code)
    if (code !== 1) {
      throw new Error(res.message || '获取个人主页失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : {}
    const memes = Array.isArray(data.memes) ? data.memes : []
    const favorites = Array.isArray(data.favorites) ? data.favorites : []

    return {
      userId: String(data.userId ?? data.id ?? id),
      nickname: data.nickname || '梗友',
      avatar: data.avatar || '',
      signature: data.signature || '这个人很懒，什么都没留下~',
      gender: data.gender != null ? Number(data.gender) : 0,
      birthday: data.birthday || '',
      isSelf: Boolean(data.isSelf),
      isFollow: Boolean(data.isFollow),
      stats: normalizeStats(data.stats),
      memes,
      favorites,
    }
  })
}

/**
 * 分页获取用户发布的梗
 * GET /user/{userId}/memes?page=&size=
 * 返回 { author, list, page, size, total, isOwner, hasMore }
 * list 项含 id/memeId、status、author、releaseTime 等
 */
export function pageUserMemes(userId, params = {}) {
  const id = userId != null ? String(userId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少用户ID'))
  }
  const { page = 1, size = 10 } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/user/${encodeURIComponent(id)}/memes?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      const data = res.data
      return {
        ...data,
        isOwner: !!(data.isOwner ?? data.owner),
      }
    }
    throw new Error((res && (res.message || res.msg)) || '获取发布列表失败')
  })
}

export function getEditProfileEcho(token) {
  if (!token) {
    return Promise.reject(new Error('未登录，无法获取编辑资料回显'))
  }
  return request('/user/me/profile', {
    method: 'GET',
    headers: withAuthHeader(token),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('编辑资料回显接口返回异常')
    }
    const code = Number(res.code)
    if (code !== 1) {
      throw new Error(res.message || '获取编辑资料回显失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : {}
    return {
      avatar: data.avatar || '',
      nickname: data.nickname || '',
      signature: data.signature || '',
      gender: data.gender != null ? Number(data.gender) : 0,
      birthday: data.birthday || '',
    }
  })
}

export function uploadUserAvatar(file, token) {
  if (!file) {
    return Promise.reject(new Error('请选择头像文件'))
  }
  const formData = new FormData()
  formData.append('file', file)
  return request('/user/avatar/upload', {
    method: 'POST',
    headers: withAuthHeader(token),
    body: formData,
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('头像上传接口返回异常')
    }
    const code = Number(res.code)
    if (code !== 1) {
      throw new Error(res.message || '头像上传失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : {}
    if (!data.url) {
      throw new Error('头像上传成功但未返回图片地址')
    }
    return {
      url: String(data.url),
      message: res.message || '头像上传成功',
    }
  })
}

export function updateUserProfile(payload = {}, token) {
  if (!token) {
    return Promise.reject(new Error('未登录，无法更新资料'))
  }
  const body = {}
  if (payload.nickname != null) {
    body.nickname = String(payload.nickname).trim()
  }
  if (payload.gender != null && payload.gender !== '') {
    body.gender = Number(payload.gender)
  }
  if (payload.birthday != null) {
    const birthday = String(payload.birthday).trim()
    body.birthday = birthday
  }
  if (payload.signature != null) {
    body.signature = String(payload.signature).trim()
  }
  if (payload.avatar != null) {
    body.avatar = String(payload.avatar).trim()
  }

  return request('/user/profile', {
    method: 'PUT',
    headers: withAuthHeader(token),
    body: JSON.stringify(body),
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('更新资料接口返回异常')
    }
    const code = Number(res.code)
    if (code !== 1) {
      throw new Error(res.message || '更新资料失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : null
    return {
      message: res.message || '资料更新成功',
      data,
    }
  })
}

/**
 * 账号设置页聚合数据
 * GET /user/me/settings → { profile, security }
 */
export function getAccountSettings(token) {
  if (!token) {
    return Promise.reject(new Error('未登录，无法获取账号设置'))
  }
  return request('/user/me/settings', {
    method: 'GET',
    headers: withAuthHeader(token),
    withCredentials: true,
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('账号设置接口返回异常')
    }
    if (Number(res.code) !== 1) {
      throw new Error(res.message || '获取账号设置失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : {}
    const profile = data.profile && typeof data.profile === 'object' ? data.profile : {}
    const security = data.security && typeof data.security === 'object' ? data.security : {}
    return {
      profile: {
        userId: profile.userId != null ? String(profile.userId) : '',
        nickname: profile.nickname || '',
        avatar: profile.avatar || '',
        signature: profile.signature || '',
        gender: profile.gender != null ? Number(profile.gender) : 0,
        birthday: profile.birthday || '',
      },
      security: {
        emailMasked: security.emailMasked || '',
        emailBound: !!security.emailBound,
        passwordSet: !!security.passwordSet,
        lastPasswordChangeTime: security.lastPasswordChangeTime || '',
        hasOtherSessions: !!security.hasOtherSessions,
      },
    }
  })
}

/**
 * 账号安全概览
 * GET /user/me/security
 */
export function getAccountSecurity(token) {
  if (!token) {
    return Promise.reject(new Error('未登录，无法获取安全概览'))
  }
  return request('/user/me/security', {
    method: 'GET',
    headers: withAuthHeader(token),
    withCredentials: true,
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('安全概览接口返回异常')
    }
    if (Number(res.code) !== 1) {
      throw new Error(res.message || '获取安全概览失败')
    }
    const security = res.data && typeof res.data === 'object' ? res.data : {}
    return {
      emailMasked: security.emailMasked || '',
      emailBound: !!security.emailBound,
      passwordSet: !!security.passwordSet,
      lastPasswordChangeTime: security.lastPasswordChangeTime || '',
      hasOtherSessions: !!security.hasOtherSessions,
    }
  })
}

/**
 * 登录态修改密码
 * PUT /user/me/password
 */
export function changeAccountPassword(payload = {}, token) {
  if (!token) {
    return Promise.reject(new Error('未登录，无法修改密码'))
  }
  const oldPassword = payload.oldPassword != null ? String(payload.oldPassword) : ''
  const newPassword = payload.newPassword != null ? String(payload.newPassword) : ''
  const confirmPassword = payload.confirmPassword != null ? String(payload.confirmPassword) : ''
  if (!oldPassword || !newPassword || !confirmPassword) {
    return Promise.reject(new Error('请填写完整的密码信息'))
  }
  return request('/user/me/password', {
    method: 'PUT',
    headers: withAuthHeader(token),
    body: JSON.stringify({ oldPassword, newPassword, confirmPassword }),
    withCredentials: true,
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('修改密码接口返回异常')
    }
    if (Number(res.code) !== 1) {
      throw new Error(res.message || '修改密码失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : {}
    return {
      requireReLogin: data.requireReLogin !== false,
      message: res.message || '密码已更新',
    }
  })
}

/**
 * 退出所有设备
 * POST /user/me/sessions/revoke-all
 */
export function revokeAllSessions(token) {
  if (!token) {
    return Promise.reject(new Error('未登录，无法退出设备'))
  }
  return request('/user/me/sessions/revoke-all', {
    method: 'POST',
    headers: withAuthHeader(token),
    withCredentials: true,
  }).then((res) => {
    if (!res || typeof res !== 'object') {
      throw new Error('退出设备接口返回异常')
    }
    if (Number(res.code) !== 1) {
      throw new Error(res.message || '退出全部设备失败')
    }
    const data = res.data && typeof res.data === 'object' ? res.data : {}
    return {
      revokedCount: Number.isFinite(Number(data.revokedCount)) ? Number(data.revokedCount) : 0,
      message: res.message || '已退出全部设备',
    }
  })
}
