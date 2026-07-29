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

export function fetchAdminTags(params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.keyword) query.set('keyword', String(params.keyword).trim())
  const qs = query.toString()
  return request(`/admin/tags${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) =>
    normalizePage(assertData(res, '获取标签失败'))
  )
}

export function createAdminTag(payload) {
  return request('/admin/tags', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '创建标签失败'))
}

export function updateAdminTag(id, payload) {
  const tagId = id != null ? String(id).trim() : ''
  if (!tagId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/tags/${encodeURIComponent(tagId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '更新标签失败'))
}

export function deleteAdminTag(id) {
  const tagId = id != null ? String(id).trim() : ''
  if (!tagId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/tags/${encodeURIComponent(tagId)}`, { method: 'DELETE' }).then((res) =>
    assertData(res, '删除标签失败')
  )
}
