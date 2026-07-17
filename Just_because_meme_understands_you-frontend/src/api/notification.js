import { request } from './request'

function unwrap(res, fallbackMsg) {
  if (!res || typeof res !== 'object') {
    throw new Error(fallbackMsg || '接口返回异常')
  }
  if (Number(res.code) !== 1) {
    throw new Error(res.message || res.msg || fallbackMsg || '请求失败')
  }
  return res.data
}

function normalizeActor(raw) {
  if (!raw || typeof raw !== 'object') return null
  const userId = raw.userId != null ? String(raw.userId).trim() : ''
  if (!userId) return null
  return {
    userId,
    nickname: raw.nickname || '梗友',
    avatar: raw.avatar || '',
  }
}

function normalizeJump(raw) {
  if (!raw || typeof raw !== 'object') return null
  const name = raw.name != null ? String(raw.name).trim() : ''
  if (!name) return null
  const params = raw.params && typeof raw.params === 'object' ? { ...raw.params } : {}
  const query = raw.query && typeof raw.query === 'object' ? { ...raw.query } : {}
  return { name, params, query }
}

function normalizeExtra(raw) {
  if (!raw || typeof raw !== 'object') return null
  const memeName = raw.memeName != null ? String(raw.memeName).trim() : ''
  const memeCover = raw.memeCover != null ? String(raw.memeCover).trim() : ''
  if (!memeName && !memeCover) return null
  return { memeName, memeCover }
}

function normalizeItem(raw) {
  if (!raw || typeof raw !== 'object') return null
  const id = raw.id != null ? String(raw.id).trim() : ''
  if (!id) return null
  return {
    id,
    type: raw.type != null ? String(raw.type).trim() : '',
    title: raw.title || '',
    content: raw.content || '',
    isRead: Boolean(raw.isRead),
    createTime: raw.createTime || '',
    actor: normalizeActor(raw.actor),
    targetType: raw.targetType != null ? String(raw.targetType).trim() : '',
    targetId: raw.targetId != null ? String(raw.targetId).trim() : '',
    refId: raw.refId != null ? String(raw.refId).trim() : '',
    jump: normalizeJump(raw.jump),
    extra: normalizeExtra(raw.extra),
  }
}

function normalizePage(data = {}, fallback = {}) {
  const list = Array.isArray(data.list)
    ? data.list.map(normalizeItem).filter(Boolean)
    : []
  return {
    list,
    page: Number.isFinite(Number(data.page)) ? Number(data.page) : Number(fallback.page) || 1,
    size: Number.isFinite(Number(data.size)) ? Number(data.size) : Number(fallback.size) || 10,
    total: Number.isFinite(Number(data.total)) ? Number(data.total) : list.length,
    hasMore: Boolean(data.hasMore),
  }
}

function normalizeUnreadCount(data = {}) {
  return {
    total: Number.isFinite(Number(data.total)) ? Number(data.total) : 0,
    interact: Number.isFinite(Number(data.interact)) ? Number(data.interact) : 0,
    system: Number.isFinite(Number(data.system)) ? Number(data.system) : 0,
  }
}

/**
 * 消息分页列表
 * GET /notifications
 */
export function getNotifications(params = {}) {
  const page = Number(params.page) > 0 ? Number(params.page) : 1
  const size = Number(params.size) > 0 ? Math.min(Number(params.size), 50) : 10
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  const type = params.type != null ? String(params.type).trim() : ''
  const tab = params.tab != null ? String(params.tab).trim() : ''
  if (type) query.set('type', type)
  if (tab) query.set('tab', tab)
  return request(`/notifications?${query}`, { method: 'GET' }).then((res) =>
    normalizePage(unwrap(res, '加载消息失败'), { page, size })
  )
}

/**
 * 未读数（顶栏）
 * GET /notifications/unread-count
 */
export function getNotificationUnreadCount() {
  return request('/notifications/unread-count', { method: 'GET' }).then((res) =>
    normalizeUnreadCount(unwrap(res, '获取未读数失败'))
  )
}

/**
 * 单条已读
 * POST /notifications/{id}/read
 */
export function markNotificationRead(notificationId) {
  const id = notificationId != null ? String(notificationId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少消息 id'))
  return request(`/notifications/${encodeURIComponent(id)}/read`, {
    method: 'POST',
    body: JSON.stringify({}),
  }).then((res) => {
    const data = unwrap(res, '标记已读失败')
    return {
      id: data.id != null ? String(data.id) : id,
      isRead: Boolean(data.isRead),
      unreadCount: Number.isFinite(Number(data.unreadCount)) ? Number(data.unreadCount) : 0,
    }
  })
}

/**
 * 删除单条消息
 * DELETE /notifications/{id}
 */
export function deleteNotification(notificationId) {
  const id = notificationId != null ? String(notificationId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少消息 id'))
  return request(`/notifications/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    const data = unwrap(res, '删除消息失败')
    return {
      id: data.id != null ? String(data.id) : id,
      deleted: Boolean(data.deleted),
      unreadCount: Number.isFinite(Number(data.unreadCount)) ? Number(data.unreadCount) : 0,
    }
  })
}

/**
 * 批量删除 / 清空
 * DELETE /notifications
 */
export function batchDeleteNotifications(payload = {}) {
  const body = {}
  if (Array.isArray(payload.ids) && payload.ids.length) {
    body.ids = payload.ids.map((id) => String(id || '').trim()).filter(Boolean)
  }
  if (payload.clearAll === true) body.clearAll = true
  if (payload.clearReadOnly === true) body.clearReadOnly = true
  return request('/notifications', {
    method: 'DELETE',
    body: JSON.stringify(body),
  }).then((res) => {
    const data = unwrap(res, '批量删除失败')
    return {
      deletedCount: Number.isFinite(Number(data.deletedCount)) ? Number(data.deletedCount) : 0,
      unreadCount: Number.isFinite(Number(data.unreadCount)) ? Number(data.unreadCount) : 0,
    }
  })
}
