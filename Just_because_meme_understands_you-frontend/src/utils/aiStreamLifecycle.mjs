const MAX_RETRY_ATTEMPT = 4
const BASE_RETRY_DELAY_MS = 250

/** 将后端旧任务状态转换为当前统一状态名。 */
export function normalizeAiTaskStatus(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'RUNNING') return 'GENERATING'
  if (value === 'CANCELLED') return 'STOPPED'
  return value
}

/** 返回从 250 毫秒开始并封顶 4 秒的指数退避时长。 */
export function nextAiStreamRetryDelay(attempt) {
  const safeAttempt = Number.isFinite(Number(attempt)) ? Math.max(0, Math.floor(Number(attempt))) : 0
  return Math.min(BASE_RETRY_DELAY_MS * (2 ** Math.min(safeAttempt, MAX_RETRY_ATTEMPT)), 4000)
}

/** 判断任务断线后是否仍可恢复，认证失败、主动停止和终态均不会重连。 */
export function shouldReconnectAiTask({ status, error, stopped = false, expiresAt, now = Date.now() } = {}) {
  if (stopped || normalizeAiTaskStatus(status) !== 'GENERATING') return false
  if (error && (error.name === 'AbortError' || error.__authHandled)) return false
  if (error && ([401, 403, 404].includes(Number(error.status)) || [401, 1002].includes(Number(error.code)))) return false
  const expiry = Number(expiresAt)
  return Number.isFinite(expiry) && expiry > Number(now)
}

/** 先关闭前端事件读取，再请求后端停止生成任务。 */
export async function stopAiStreamTask({ taskId, status, stopReceiving, requestStop } = {}) {
  if (typeof stopReceiving === 'function') stopReceiving()
  const id = String(taskId || '').trim()
  if (!id || normalizeAiTaskStatus(status) !== 'GENERATING') return null
  if (typeof requestStop !== 'function') throw new TypeError('requestStop 必须是函数')
  return requestStop(id)
}
