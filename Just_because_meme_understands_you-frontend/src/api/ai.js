import { request, streamRequest } from './request'
import { readAiStreamSse } from '@/utils/aiStreamSse.mjs'
import { normalizeAiTaskStatus } from '@/utils/aiStreamLifecycle.mjs'

function unwrap(res, fallbackMsg) {
  if (!res || typeof res !== 'object') {
    throw new Error(fallbackMsg || '接口返回异常')
  }
  if (Number(res.code) !== 1) {
    throw new Error(res.message || res.msg || fallbackMsg || '请求失败')
  }
  return res.data
}

function normalizeSession(raw) {
  if (!raw || typeof raw !== 'object') return null
  const id = raw.id != null ? String(raw.id).trim() : ''
  if (!id) return null
  return {
    id,
    title: raw.title || '新对话',
    createTime: raw.createTime || '',
    updateTime: raw.updateTime || '',
  }
}

function normalizeMessage(raw) {
  if (!raw || typeof raw !== 'object') return null
  const id = raw.id != null ? String(raw.id).trim() : ''
  if (!id) return null
  return {
    id,
    sessionId: raw.sessionId != null ? String(raw.sessionId) : '',
    role: raw.role != null ? String(raw.role) : '',
    status: normalizeAiTaskStatus(raw.status || 'COMPLETED'),
    content: raw.content || '',
    requestId: raw.requestId || '',
    tokenEstimate: Number.isFinite(Number(raw.tokenEstimate)) ? Number(raw.tokenEstimate) : 0,
    createTime: raw.createTime || '',
  }
}

function normalizePage(data, itemMapper) {
  const src = data && typeof data === 'object' ? data : {}
  const list = Array.isArray(src.list) ? src.list.map(itemMapper).filter(Boolean) : []
  return {
    list,
    page: Number.isFinite(Number(src.page)) ? Number(src.page) : 1,
    size: Number.isFinite(Number(src.size)) ? Number(src.size) : 10,
    total: Number.isFinite(Number(src.total)) ? Number(src.total) : list.length,
    hasMore: Boolean(src.hasMore),
  }
}

function normalizeTask(raw) {
  if (!raw || typeof raw !== 'object') return null
  const taskId = raw.taskId != null ? String(raw.taskId).trim() : ''
  if (!taskId) return null
  return {
    taskId,
    sessionId: raw.sessionId != null ? String(raw.sessionId) : '',
    requestId: raw.requestId != null ? String(raw.requestId) : '',
    status: normalizeAiTaskStatus(raw.status),
    expiresAtMillis: Number.isFinite(Number(raw.expiresAtMillis)) ? Number(raw.expiresAtMillis) : 0,
  }
}

/**
 * 会话列表
 * GET /ai/sessions
 */
export function getAiSessions(params = {}) {
  const page = params.page != null ? Number(params.page) : 1
  const size = params.size != null ? Number(params.size) : 10
  const query = new URLSearchParams({
    page: String(page > 0 ? page : 1),
    size: String(Math.min(Math.max(size || 10, 1), 50)),
  })
  return request(`/ai/sessions?${query}`, { method: 'GET' }).then((res) =>
    normalizePage(unwrap(res, '加载会话失败'), normalizeSession)
  )
}

/**
 * 会话消息分页
 * GET /ai/sessions/{sessionId}/messages
 */
export function getAiSessionMessages(sessionId, params = {}) {
  const id = sessionId != null ? String(sessionId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少 sessionId'))
  const page = params.page != null ? Number(params.page) : 1
  const size = params.size != null ? Number(params.size) : 20
  const query = new URLSearchParams({
    page: String(page > 0 ? page : 1),
    size: String(Math.min(Math.max(size || 20, 1), 50)),
  })
  return request(`/ai/sessions/${encodeURIComponent(id)}/messages?${query}`, { method: 'GET' }).then(
    (res) => normalizePage(unwrap(res, '加载消息失败'), normalizeMessage)
  )
}

/**
 * 删除会话
 * DELETE /ai/sessions/{sessionId}
 */
export function deleteAiSession(sessionId) {
  const id = sessionId != null ? String(sessionId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少 sessionId'))
  return request(`/ai/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    unwrap(res, '删除会话失败')
    return true
  })
}

/** 创建后台 AI 搜索任务，浏览器断开后后端仍会继续生成。 */
export async function createAiSearchTask(payload = {}) {
  const query = payload.query != null ? String(payload.query).trim() : ''
  if (!query) throw new Error('query 不能为空')
  const body = { query }
  if (payload.sessionId != null && String(payload.sessionId).trim()) {
    body.sessionId = String(payload.sessionId).trim()
  }
  if (payload.requestId != null && String(payload.requestId).trim()) {
    body.requestId = String(payload.requestId).trim()
  }
  const data = unwrap(await request('/ai/searches', {
    method: 'POST',
    body: JSON.stringify(body),
  }), '创建 AI 搜索失败')
  const task = normalizeTask(data)
  if (!task) throw new Error('AI 搜索任务响应缺少 taskId')
  return task
}

/** 查询当前登录用户尚在保留期内的 AI 搜索任务。 */
export async function getCurrentAiSearchTask() {
  const data = unwrap(await request('/ai/searches/current', { method: 'GET' }), '恢复 AI 搜索失败')
  return normalizeTask(data)
}

/** 从指定事件 ID 之后读取后台任务 SSE，断线时可再次调用恢复。 */
export async function streamAiSearchTask(taskId, afterEventId = 0, handlers = {}) {
  const id = taskId != null ? String(taskId).trim() : ''
  if (!id) throw new Error('缺少 taskId')
  const cursor = Number.isFinite(Number(afterEventId)) ? Math.max(0, Number(afterEventId)) : 0
  const query = new URLSearchParams({ after: String(cursor) })
  const response = await streamRequest(`/ai/searches/${encodeURIComponent(id)}/events?${query}`, {
    method: 'GET',
    signal: handlers.signal,
  })
  if (!response.ok) {
    let message = `AI 搜索事件读取失败: ${response.status}`
    try {
      const json = await response.clone().json()
      message = json.message || json.msg || message
    } catch {
      // 响应不是 JSON 时保留 HTTP 状态作为错误信息。
    }
    const error = new Error(message)
    error.status = response.status
    throw error
  }
  return readAiStreamSse(response, handlers)
}

/** 请求后端取消任务；切换页面只应停止事件读取器。 */
export async function cancelAiSearchTask(taskId) {
  const id = taskId != null ? String(taskId).trim() : ''
  if (!id) throw new Error('缺少 taskId')
  const data = unwrap(await request(`/ai/searches/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
  }), '停止 AI 搜索失败')
  const task = normalizeTask(data)
  if (!task) throw new Error('停止 AI 搜索响应缺少 taskId')
  return task
}

/**
 * AI 搜索流式（SSE）
 * POST /ai/searches
 *
 * @param {{ query: string, sessionId?: string, requestId?: string }} payload
 * @param {{
 *   onSession?: (data: {session_id: string, request_id: string}) => void,
 *   onMeta?: (data: object) => void,
 *   onCite?: (data: object) => void,
 *   onToken?: (data: {text: string}) => void,
 *   onDone?: (data: object) => void,
 *   onError?: (data: object) => void,
 *   signal?: AbortSignal,
 * }} handlers
 */
export async function streamAiSearch(payload = {}, handlers = {}) {
  const query = payload.query != null ? String(payload.query).trim() : ''
  if (!query) throw new Error('query 不能为空')

  const body = { query }
  if (payload.sessionId != null && String(payload.sessionId).trim()) {
    body.sessionId = String(payload.sessionId).trim()
  }
  if (payload.requestId != null && String(payload.requestId).trim()) {
    body.requestId = String(payload.requestId).trim()
  }

  const response = await streamRequest('/ai/searches', {
    method: 'POST',
    body: JSON.stringify(body),
    signal: handlers.signal,
  })

  if (!response.ok) {
    let message = `AI 搜索失败: ${response.status}`
    try {
      const json = await response.json()
      message = json.message || json.msg || message
    } catch {
      // ignore
    }
    throw new Error(message)
  }

  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'

  let streamOpen = true
  while (streamOpen) {
    const { done, value } = await reader.read()
    if (done) {
      streamOpen = false
      break
    }
    buffer += decoder.decode(value, { stream: true })

    let sep
    while ((sep = buffer.indexOf('\n')) >= 0) {
      let line = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 1)
      if (line.endsWith('\r')) line = line.slice(0, -1)

      if (!line) {
        eventName = 'message'
        continue
      }
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim() || 'message'
        continue
      }
      if (!line.startsWith('data:')) continue

      const raw = line.slice(5).trim()
      let data = raw
      try {
        data = JSON.parse(raw)
      } catch {
        // keep string
      }

      // Spring SSE 可能省略 event 行；按 JSON 字段推断事件类型
      let resolvedEvent = eventName
      if (
        (resolvedEvent === 'message' || !resolvedEvent) &&
        data &&
        typeof data === 'object'
      ) {
        if (data.session_id != null || data.sessionId != null) resolvedEvent = 'session'
        else if (Array.isArray(data.retrieved) || data.request_id != null) resolvedEvent = 'meta'
        else if (data.meme_id != null || data.memeId != null) resolvedEvent = 'cite'
        else if (data.text != null) resolvedEvent = 'token'
        else if (data.finish_reason != null) resolvedEvent = 'done'
        else if (data.code != null && data.message != null) resolvedEvent = 'error'
      }

      if (resolvedEvent === 'session' && typeof handlers.onSession === 'function') {
        handlers.onSession(data)
      } else if (resolvedEvent === 'meta' && typeof handlers.onMeta === 'function') {
        handlers.onMeta(data)
      } else if (resolvedEvent === 'cite' && typeof handlers.onCite === 'function') {
        handlers.onCite(data)
      } else if (resolvedEvent === 'token' && typeof handlers.onToken === 'function') {
        handlers.onToken(data)
      } else if (resolvedEvent === 'done' && typeof handlers.onDone === 'function') {
        handlers.onDone(data)
      } else if (resolvedEvent === 'error' && typeof handlers.onError === 'function') {
        handlers.onError(data)
      }
    }
  }
}
