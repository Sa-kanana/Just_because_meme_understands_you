import { request } from './request'

export function submitMemeReport(memeId, payload = {}) {
  const id = String(memeId || '').trim()
  if (!id) return Promise.reject(new Error('缺少梗 id'))
  return request(`/memes/${encodeURIComponent(id)}/reports`, {
    method: 'POST',
    body: JSON.stringify({
      reason: String(payload.reason || ''),
      detail: String(payload.detail || '').trim(),
    }),
  }).then((res) => {
    if (res && Number(res.code) === 1) return res.data
    throw new Error((res && (res.message || res.msg)) || '举报提交失败')
  })
}

export function submitCommentReport(commentId, payload = {}) {
  const id = String(commentId ?? '').trim()
  if (!/^\d+$/.test(id)) return Promise.reject(new Error('评论 id 不合法'))
  return request(`/comments/${encodeURIComponent(id)}/reports`, {
    method: 'POST',
    body: JSON.stringify({
      reason: String(payload.reason || ''),
      detail: String(payload.detail || '').trim(),
    }),
  }).then((res) => {
    if (res && Number(res.code) === 1) return res.data
    throw new Error((res && (res.message || res.msg)) || '举报提交失败')
  })
}
