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

export function fetchAdminSensitiveWords(params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.keyword) query.set('keyword', String(params.keyword).trim())
  if (params.category) query.set('category', String(params.category).trim())
  if (params.status != null && params.status !== '') query.set('status', String(params.status))
  const qs = query.toString()
  return request(`/admin/sensitive-words${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) =>
    normalizePage(assertData(res, '获取敏感词失败'))
  )
}

export function createAdminSensitiveWord(payload) {
  return request('/admin/sensitive-words', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '创建敏感词失败'))
}

export function updateAdminSensitiveWord(id, payload) {
  const wordId = id != null ? String(id).trim() : ''
  if (!wordId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/sensitive-words/${encodeURIComponent(wordId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '更新敏感词失败'))
}

export function deleteAdminSensitiveWord(id) {
  const wordId = id != null ? String(id).trim() : ''
  if (!wordId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/sensitive-words/${encodeURIComponent(wordId)}`, {
    method: 'DELETE',
  }).then((res) => assertData(res, '删除敏感词失败'))
}

export function updateAdminSensitiveWordStatus(id, status) {
  const wordId = id != null ? String(id).trim() : ''
  if (!wordId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/sensitive-words/${encodeURIComponent(wordId)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }).then((res) => assertData(res, '更新敏感词状态失败'))
}
