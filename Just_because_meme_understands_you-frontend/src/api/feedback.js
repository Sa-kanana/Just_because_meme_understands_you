import { request } from './request'

/**
 * 获取反馈元数据（类型选项）
 * GET /feedback/meta
 * @returns {Promise<{ categories: Array<{ value: string, label: string }>, notice: string }>}
 */
export function getFeedbackMeta() {
  return request('/feedback/meta', { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      const data = res.data
      return {
        categories: Array.isArray(data.categories) ? data.categories : [],
        notice: data.notice != null ? String(data.notice) : '',
      }
    }
    throw new Error((res && (res.message || res.msg)) || '加载反馈选项失败')
  })
}

/**
 * 提交反馈
 * POST /feedback
 * @param {{ category: string, content: string, contactEmail?: string, pageUrl?: string }} payload
 * @returns {Promise<{ accepted: boolean, message: string }>}
 */
export function submitFeedback(payload = {}) {
  const category = payload.category != null ? String(payload.category).trim() : ''
  const content = payload.content != null ? String(payload.content).trim() : ''
  const contactEmail = payload.contactEmail != null ? String(payload.contactEmail).trim() : ''
  const pageUrl = payload.pageUrl != null ? String(payload.pageUrl).trim() : ''

  if (!category) {
    return Promise.reject(new Error('请选择反馈类型'))
  }
  if (!content || content.length < 5) {
    return Promise.reject(new Error('反馈内容至少 5 个字'))
  }

  const body = { category, content }
  if (contactEmail) body.contactEmail = contactEmail
  if (pageUrl) body.pageUrl = pageUrl

  return request('/feedback', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      return {
        accepted: Boolean(res.data.accepted),
        message: res.data.message != null ? String(res.data.message) : '反馈已提交',
      }
    }
    throw new Error((res && (res.message || res.msg)) || '提交失败')
  })
}
