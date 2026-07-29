import { request } from './request'

function normalizePage(data) {
  const raw = data && typeof data === 'object' ? data : {}
  const list = Array.isArray(raw.list) ? raw.list : []
  return {
    list,
    page: Number(raw.page) || 1,
    size: Number(raw.size) || 10,
    total: Number(raw.total) || 0,
    hasMore: !!raw.hasMore,
  }
}

function assertData(res, msg) {
  if (res && Number(res.code) === 1) return res.data
  throw new Error((res && (res.message || res.msg)) || msg || '请求失败')
}

export function fetchAdminUsers(params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.status != null && params.status !== '') query.set('status', String(params.status))
  if (params.role) query.set('role', String(params.role).trim())
  if (params.keyword) query.set('keyword', String(params.keyword).trim())
  const qs = query.toString()
  return request(`/admin/users${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) =>
    normalizePage(assertData(res, '获取用户列表失败'))
  )
}

export function updateAdminUserStatus(id, status) {
  const userId = id != null ? String(id).trim() : ''
  if (!userId) return Promise.reject(new Error('缺少 userId'))
  return request(`/admin/users/${encodeURIComponent(userId)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }).then((res) => assertData(res, '更新用户状态失败'))
}

export function updateAdminUserRole(id, role) {
  const userId = id != null ? String(id).trim() : ''
  if (!userId) return Promise.reject(new Error('缺少 userId'))
  return request(`/admin/users/${encodeURIComponent(userId)}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  }).then((res) => assertData(res, '更新用户角色失败'))
}
