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

export function fetchAdminKnowledge(params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.keyword) query.set('keyword', String(params.keyword).trim())
  if (params.category) query.set('category', String(params.category).trim())
  const qs = query.toString()
  return request(`/admin/knowledge${qs ? `?${qs}` : ''}`, { method: 'GET' }).then((res) =>
    normalizePage(assertData(res, '获取知识库失败'))
  )
}

export function createAdminKnowledge(payload) {
  return request('/admin/knowledge', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '创建知识失败'))
}

/**
 * 上传 Markdown / PDF，服务端提取文本后入库并灌库。
 * POST /admin/knowledge/upload multipart: file, title?, category?, tags?
 */
export function uploadAdminKnowledgeFile({
  file,
  title = '',
  category = '',
  tags = [],
} = {}) {
  if (!file) {
    return Promise.reject(new Error('请选择文件'))
  }
  const name = String(file.name || '').toLowerCase()
  if (!/\.(md|markdown|pdf)$/.test(name)) {
    return Promise.reject(new Error('仅支持 .md / .markdown / .pdf'))
  }
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', String(title).trim())
  if (category) form.append('category', String(category).trim())
  const tagText = Array.isArray(tags)
    ? tags.filter(Boolean).join(',')
    : String(tags || '').trim()
  if (tagText) form.append('tags', tagText)
  return request('/admin/knowledge/upload', {
    method: 'POST',
    body: form,
    timeout: 120000,
  }).then((res) => assertData(res, '上传知识文件失败'))
}

export function updateAdminKnowledge(id, payload) {
  const docId = id != null ? String(id).trim() : ''
  if (!docId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/knowledge/${encodeURIComponent(docId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload || {}),
  }).then((res) => assertData(res, '更新知识失败'))
}

export function deleteAdminKnowledge(id) {
  const docId = id != null ? String(id).trim() : ''
  if (!docId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/knowledge/${encodeURIComponent(docId)}`, { method: 'DELETE' }).then(
    (res) => assertData(res, '删除知识失败')
  )
}

export function reindexAdminKnowledge(id) {
  const docId = id != null ? String(id).trim() : ''
  if (!docId) return Promise.reject(new Error('缺少 id'))
  return request(`/admin/knowledge/${encodeURIComponent(docId)}/reindex`, {
    method: 'POST',
  }).then((res) => assertData(res, '重新灌库失败'))
}
