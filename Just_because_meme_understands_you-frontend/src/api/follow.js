import { request } from './request'

function withAuthHeader(token) {
  const raw = token != null ? String(token).trim() : ''
  if (!raw) return {}
  return {
    Authorization: raw.toLowerCase().startsWith('bearer ') ? raw : `Bearer ${raw}`,
  }
}

function assertUserId(userId) {
  const id = userId != null ? String(userId).trim() : ''
  if (!id || !/^\d+$/.test(id)) {
    throw new Error('用户ID格式不正确')
  }
  return id
}

function unwrap(res, fallbackMsg) {
  if (!res || typeof res !== 'object') {
    throw new Error(fallbackMsg || '接口返回异常')
  }
  if (Number(res.code) !== 1) {
    throw new Error(res.message || res.msg || fallbackMsg || '请求失败')
  }
  return res.data
}

function normalizeFollowed(data = {}) {
  return {
    userId: data.userId != null ? String(data.userId) : '',
    followed: Boolean(data.followed),
    followCount: Number.isFinite(Number(data.followCount)) ? Number(data.followCount) : 0,
    fansCount: Number.isFinite(Number(data.fansCount)) ? Number(data.fansCount) : 0,
    mutual: Boolean(data.mutual),
  }
}

function normalizeListItem(raw) {
  if (!raw || typeof raw !== 'object') return null
  const userId = raw.userId != null ? String(raw.userId).trim() : ''
  if (!userId) return null
  return {
    userId,
    nickname: raw.nickname || '梗友',
    avatar: raw.avatar || '',
    signature: raw.signature || '',
    followTime: raw.followTime || '',
    followedByMe: Boolean(raw.followedByMe),
    mutual: Boolean(raw.mutual),
  }
}

function normalizeListPage(data = {}) {
  const list = Array.isArray(data.list)
    ? data.list.map(normalizeListItem).filter(Boolean)
    : []
  return {
    list,
    page: Number.isFinite(Number(data.page)) ? Number(data.page) : 1,
    size: Number.isFinite(Number(data.size)) ? Number(data.size) : 20,
    total: Number.isFinite(Number(data.total)) ? Number(data.total) : list.length,
    hasMore: Boolean(data.hasMore),
  }
}

/** 关注用户 POST /user/{userId}/follow */
export function followUser(userId, token) {
  const id = assertUserId(userId)
  return request(`/user/${encodeURIComponent(id)}/follow`, {
    method: 'POST',
    headers: withAuthHeader(token),
    body: JSON.stringify({}),
  }).then((res) => normalizeFollowed(unwrap(res, '关注失败')))
}

/** 取消关注 DELETE /user/{userId}/follow */
export function unfollowUser(userId, token) {
  const id = assertUserId(userId)
  return request(`/user/${encodeURIComponent(id)}/follow`, {
    method: 'DELETE',
    headers: withAuthHeader(token),
  }).then((res) => normalizeFollowed(unwrap(res, '取消关注失败')))
}

/** 单人关注状态 GET /user/{userId}/follow/status */
export function getFollowStatus(userId, token) {
  const id = assertUserId(userId)
  return request(`/user/${encodeURIComponent(id)}/follow/status`, {
    method: 'GET',
    headers: withAuthHeader(token),
  }).then((res) => normalizeFollowed(unwrap(res, '获取关注状态失败')))
}

/**
 * 批量关注状态 GET /user/follow/status?userIds=1,2,3
 * @returns {Map<string, boolean>} userId -> followed
 */
export function batchFollowStatus(userIds, token) {
  const ids = (Array.isArray(userIds) ? userIds : [])
    .map((id) => String(id).trim())
    .filter((id) => /^\d+$/.test(id))
  if (!ids.length) {
    return Promise.resolve(new Map())
  }
  const unique = [...new Set(ids)].slice(0, 50)
  const query = new URLSearchParams({ userIds: unique.join(',') })
  return request(`/user/follow/status?${query}`, {
    method: 'GET',
    headers: withAuthHeader(token),
  }).then((res) => {
    const data = unwrap(res, '获取关注状态失败') || {}
    const items = Array.isArray(data.items) ? data.items : []
    const map = new Map()
    items.forEach((item) => {
      if (!item || item.userId == null) return
      map.set(String(item.userId), Boolean(item.followed))
    })
    return map
  })
}

/** 关注列表 GET /user/{userId}/following */
export function pageFollowing(userId, params = {}, token) {
  const id = assertUserId(userId)
  const page = params.page != null ? Number(params.page) : 1
  const size = params.size != null ? Number(params.size) : 20
  const query = new URLSearchParams({
    page: String(Number.isFinite(page) && page > 0 ? page : 1),
    size: String(Number.isFinite(size) && size > 0 ? Math.min(size, 50) : 20),
  })
  return request(`/user/${encodeURIComponent(id)}/following?${query}`, {
    method: 'GET',
    headers: withAuthHeader(token),
  }).then((res) => normalizeListPage(unwrap(res, '获取关注列表失败')))
}

/** 粉丝列表 GET /user/{userId}/followers */
export function pageFollowers(userId, params = {}, token) {
  const id = assertUserId(userId)
  const page = params.page != null ? Number(params.page) : 1
  const size = params.size != null ? Number(params.size) : 20
  const query = new URLSearchParams({
    page: String(Number.isFinite(page) && page > 0 ? page : 1),
    size: String(Number.isFinite(size) && size > 0 ? Math.min(size, 50) : 20),
  })
  return request(`/user/${encodeURIComponent(id)}/followers?${query}`, {
    method: 'GET',
    headers: withAuthHeader(token),
  }).then((res) => normalizeListPage(unwrap(res, '获取粉丝列表失败')))
}
