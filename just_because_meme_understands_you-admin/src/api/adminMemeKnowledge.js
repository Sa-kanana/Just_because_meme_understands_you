import { request } from './request'

function dataOf(response) {
  if (response && Number(response.code) === 1) return response.data
  throw new Error((response && response.message) || '纠错审核操作失败')
}

export function fetchMemeKnowledgeCorrections({ page = 1, size = 10, status = 'PENDING' } = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return request(`/admin/meme-knowledge-corrections?${query}`, { method: 'GET' }).then(dataOf)
}

export function resolveMemeKnowledgeCorrection(id, decision, note = '') {
  return request(`/admin/meme-knowledge-corrections/${encodeURIComponent(String(id))}`, {
    method: 'PATCH', body: { decision, note },
  }).then(dataOf)
}
