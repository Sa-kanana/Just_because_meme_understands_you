import { request } from './request'

function assertData(res, msg) {
  if (res && Number(res.code) === 1) return res.data
  throw new Error((res && (res.message || res.msg)) || msg || '请求失败')
}

/** GET /admin/ai/operations */
export function fetchAiOpsStatus() {
  return request('/admin/ai/operations', { method: 'GET' }).then((res) =>
    assertData(res, '获取运维状态失败')
  )
}

/**
 * POST /admin/ai/crawl-jobs?limit=
 * 爬取可能较久，超时放宽到 3 分钟。
 */
export function triggerAiCrawl(limit) {
  const query = new URLSearchParams()
  if (limit != null && limit !== '') query.set('limit', String(limit))
  const qs = query.toString()
  return request(`/admin/ai/crawl-jobs${qs ? `?${qs}` : ''}`, {
    method: 'POST',
    timeout: 180000,
  }).then((res) => assertData(res, '触发爬虫失败'))
}

export function triggerAiIngestBackfill(limit) {
  const query = new URLSearchParams()
  if (limit != null && limit !== '') query.set('limit', String(limit))
  const qs = query.toString()
  return request(`/admin/ai/ingestion-jobs${qs ? `?${qs}` : ''}`, {
    method: 'POST',
    timeout: 120000,
  }).then((res) => assertData(res, '触发回填失败'))
}

export function triggerAiIngestSync(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少 memeId'))
  return request(`/admin/ai/ingestion-jobs/memes/${encodeURIComponent(id)}`, { method: 'POST' }).then((res) =>
    assertData(res, '同步向量失败')
  )
}

export function triggerAiIngestApprove(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少 memeId'))
  return request(`/admin/ai/ingestion-jobs/memes/${encodeURIComponent(id)}/approvals`, { method: 'POST' }).then((res) =>
    assertData(res, '审核灌库失败')
  )
}
