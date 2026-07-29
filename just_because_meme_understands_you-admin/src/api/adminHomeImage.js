import { request } from './request'

function normalizePage(data) {
  const raw = data && typeof data === 'object' ? data : {}
  return {
    list: Array.isArray(raw.list) ? raw.list : [],
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

export function fetchAdminHomeImages(params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.status != null && params.status !== '') query.set('status', String(params.status))
  const qs = query.toString()
  return request(`/admin/home-images${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) =>
    normalizePage(assertData(res, '获取轮播失败'))
  )
}

export function createAdminHomeImage(payload) {
  return request('/admin/home-images', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '创建轮播失败'))
}

export function updateAdminHomeImage(id, payload) {
  const imageId = id != null ? String(id).trim() : ''
  if (!imageId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/home-images/${encodeURIComponent(imageId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '更新轮播失败'))
}

export function deleteAdminHomeImage(id) {
  const imageId = id != null ? String(id).trim() : ''
  if (!imageId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/home-images/${encodeURIComponent(imageId)}`, { method: 'DELETE' }).then(
    (res) => assertData(res, '删除轮播失败')
  )
}

export function updateAdminHomeImageStatus(id, status) {
  const imageId = id != null ? String(id).trim() : ''
  if (!imageId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/home-images/${encodeURIComponent(imageId)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }).then((res) => assertData(res, '更新轮播状态失败'))
}
