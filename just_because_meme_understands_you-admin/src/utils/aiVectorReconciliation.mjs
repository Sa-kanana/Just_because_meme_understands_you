/** 中文注释：管理端请求梗向量校准任务的基础路径。 */
const RECONCILIATION_API_PATH = '/admin/ai/vector-index/reconciliation-jobs'

/** 中文注释：允许续跑和轮询中的后台任务状态集合。 */
const ACTIVE_RECONCILIATION_STATUSES = new Set(['PENDING', 'RUNNING'])

/** 中文注释：判断校准任务是否仍在后台运行。 */
export function isVectorReconciliationActive(status) {
  return ACTIVE_RECONCILIATION_STATUSES.has(String(status || '').toUpperCase())
}

/** 中文注释：失败任务保留游标，可由管理员从最后确认页继续。 */
export function canResumeVectorReconciliation(status) {
  return String(status || '').toUpperCase() === 'FAILED'
}

/** 中文注释：将后端任务状态映射成管理端可读文案。 */
export function vectorReconciliationStatusLabel(status) {
  switch (String(status || '').toUpperCase()) {
    case 'PENDING':
      return '等待处理'
    case 'RUNNING':
      return '校准中'
    case 'FAILED':
      return '失败，可继续'
    case 'COMPLETED':
      return '已完成（事件已入队）'
    default:
      return '未知状态'
  }
}

/** 中文注释：校验十进制任务 ID，避免将路径片段注入管理端请求路径。 */
export function vectorReconciliationJobPath(jobId) {
  const normalizedId = jobId == null ? '' : String(jobId)
  if (!/^[1-9]\d*$/.test(normalizedId)) {
    throw new Error('校准任务 ID 不合法')
  }
  return `${RECONCILIATION_API_PATH}/${encodeURIComponent(normalizedId)}`
}

/** 中文注释：提供任务列表和当前页需要的 API 路径，不暴露请求对象。 */
export function vectorReconciliationCollectionPath() {
  return RECONCILIATION_API_PATH
}
