import { request } from './request'

function assertData(res, msg) {
  if (res && Number(res.code) === 1) return res.data
  throw new Error((res && (res.message || res.msg)) || msg || '请求失败')
}

/** GET /ai/ops/status */
export function fetchAiOpsStatus() {
  return request('/ai/ops/status', { method: 'GET' }).then((res) =>
    assertData(res, '获取运维状态失败')
  )
}

/**
 * POST /ai/crawl/trigger?limit=
 * 爬取可能较久，超时放宽到 3 分钟。
 */
export function triggerAiCrawl(limit) {
  const query = new URLSearchParams()
  if (limit != null && limit !== '') query.set('limit', String(limit))
  const qs = query.toString()
  return request(`/ai/crawl/trigger${qs ? `?${qs}` : ''}`, {
    method: 'POST',
    timeout: 180000,
  }).then((res) => assertData(res, '触发爬虫失败'))
}

export function triggerAiIngestBackfill(limit) {
  const query = new URLSearchParams()
  if (limit != null && limit !== '') query.set('limit', String(limit))
  const qs = query.toString()
  return request(`/ai/ingest/backfill${qs ? `?${qs}` : ''}`, {
    method: 'POST',
    timeout: 120000,
  }).then((res) => assertData(res, '触发回填失败'))
}

export function triggerAiIngestSync(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少 memeId'))
  return request(`/ai/ingest/sync/${encodeURIComponent(id)}`, { method: 'POST' }).then((res) =>
    assertData(res, '同步向量失败')
  )
}

export function triggerAiIngestApprove(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少 memeId'))
  return request(`/ai/ingest/approve/${encodeURIComponent(id)}`, { method: 'POST' }).then((res) =>
    assertData(res, '审核灌库失败')
  )
}
