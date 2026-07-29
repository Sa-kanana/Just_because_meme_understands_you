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
  if (res && Number(res.code) === 1) {
    return res.data
  }
  throw new Error((res && (res.message || res.msg)) || msg || '请求失败')
}

export function fetchAdminMemes(params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.status != null && params.status !== '') query.set('status', String(params.status))
  if (params.keyword) query.set('keyword', String(params.keyword).trim())
  if (params.userId) query.set('userId', String(params.userId).trim())
  const qs = query.toString()
  return request(`/admin/memes${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) =>
    normalizePage(assertData(res, '获取梗列表失败'))
  )
}

export function approveAdminMeme(id) {
  const memeId = id != null ? String(id).trim() : ''
  if (!memeId) return Promise.reject(new Error('缺少 memeId'))
  return request(`/admin/memes/${encodeURIComponent(memeId)}/approve`, { method: 'POST' }).then(
    (res) => assertData(res, '审核通过失败')
  )
}

export function rejectAdminMeme(id) {
  const memeId = id != null ? String(id).trim() : ''
  if (!memeId) return Promise.reject(new Error('缺少 memeId'))
  return request(`/admin/memes/${encodeURIComponent(memeId)}/reject`, { method: 'POST' }).then(
    (res) => assertData(res, '拒绝失败')
  )
}

export function offlineAdminMeme(id) {
  const memeId = id != null ? String(id).trim() : ''
  if (!memeId) return Promise.reject(new Error('缺少 memeId'))
  return request(`/admin/memes/${encodeURIComponent(memeId)}/offline`, { method: 'POST' }).then(
    (res) => assertData(res, '下架失败')
  )
}

export function onlineAdminMeme(id) {
  const memeId = id != null ? String(id).trim() : ''
  if (!memeId) return Promise.reject(new Error('缺少 memeId'))
  return request(`/admin/memes/${encodeURIComponent(memeId)}/online`, { method: 'POST' }).then(
    (res) => assertData(res, '提交审核失败')
  )
}
