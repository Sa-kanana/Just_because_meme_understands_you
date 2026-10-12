import { request } from './request'
import {
  vectorReconciliationCollectionPath,
  vectorReconciliationJobPath,
} from '@/utils/aiVectorReconciliation.mjs'

function assertData(res, msg) {
  if (res && Number(res.code) === 1) return res.data
  throw new Error((res && (res.message || res.msg)) || msg || '请求失败')
}

/** 中文注释：手动启动梗向量库双向校准，已有活动任务时后端返回该任务。 */
export function startAiVectorReconciliation() {
  return request(vectorReconciliationCollectionPath(), { method: 'POST' }).then((res) =>
    assertData(res, '启动梗向量校准失败')
  )
}

/** 中文注释：读取任务状态前先校验任务 ID，防止无效路径请求。 */
export function fetchAiVectorReconciliationJob(jobId) {
  return request(vectorReconciliationJobPath(jobId), { method: 'GET' }).then((res) =>
    assertData(res, '获取梗向量校准状态失败')
  )
}

/** 中文注释：继续失败任务；后端从最后确认的分页游标继续处理。 */
export function resumeAiVectorReconciliation(jobId) {
  return request(`${vectorReconciliationJobPath(jobId)}/resume`, { method: 'POST' }).then((res) =>
    assertData(res, '继续梗向量校准失败')
  )
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
