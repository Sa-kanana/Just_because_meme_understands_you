import { request } from './request'
import { useAuthStore } from '@/stores/auth'
import { isAuthExpiredError, markAuthErrorHandled } from '@/utils/authSession'

const BASE_URL = process.env.VUE_APP_API_BASE_URL || '/api'

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

/**
 * 回填已发布梗到向量库（运维）
 * POST /ai/ingest/backfill
 */
export function backfillAiIngest(limit) {
  const query = new URLSearchParams()
  if (limit != null && Number(limit) > 0) {
    query.set('limit', String(Math.min(Number(limit), 2000)))
  }
  const suffix = query.toString() ? `?${query}` : ''
  return request(`/ai/ingest/backfill${suffix}`, { method: 'POST' }).then((res) =>
    unwrap(res, '回填失败')
  )
}

/**
 * AI 搜索流式（SSE）
 * POST /ai/search/stream
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

  const authStore = useAuthStore()
  const token = authStore.accessToken || ''
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (token) {
    headers.Authorization = token.toLowerCase().startsWith('bearer ') ? token : `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}/ai/search/stream`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify(body),
    signal: handlers.signal,
  })

  if (response.status === 401) {
    const err = new Error('登录已过期，请重新登录')
    err.code = 401
    err.status = 401
    if (isAuthExpiredError(err)) {
      throw markAuthErrorHandled(err)
    }
    throw err
  }

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

      if (eventName === 'session' && typeof handlers.onSession === 'function') {
        handlers.onSession(data)
      } else if (eventName === 'meta' && typeof handlers.onMeta === 'function') {
        handlers.onMeta(data)
      } else if (eventName === 'cite' && typeof handlers.onCite === 'function') {
        handlers.onCite(data)
      } else if (eventName === 'token' && typeof handlers.onToken === 'function') {
        handlers.onToken(data)
      } else if (eventName === 'done' && typeof handlers.onDone === 'function') {
        handlers.onDone(data)
      } else if (eventName === 'error' && typeof handlers.onError === 'function') {
        handlers.onError(data)
      }
    }
  }
}
