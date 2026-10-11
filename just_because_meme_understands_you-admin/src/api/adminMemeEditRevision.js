import { request } from './request'

/** 中文注释：从成功响应中取出数据；异常信息优先使用服务端说明。 */
function dataOf(response, fallbackMessage) {
  if (response && Number(response.code) === 1) return response.data
  throw new Error((response && (response.message || response.msg)) || fallbackMessage)
}

/** 中文注释：分页读取等待审核的作者修改稿。 */
export function fetchAdminMemeEditRevisions({ page = 1, size = 10 } = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) }) // 中文注释：审核队列分页参数。
  return request(`/meme-edit-revisions?${query}`, { method: 'GET' }).then((response) =>
    dataOf(response, '获取修改稿审核队列失败')
  )
}

/** 中文注释：读取当前正式内容与作者修改内容的审核对照。 */
export function fetchAdminMemeEditRevision(revisionId) {
  const id = revisionId != null ? String(revisionId).trim() : '' // 中文注释：修改稿审核记录 ID。
  if (!/^\d+$/.test(id)) return Promise.reject(new Error('缺少有效的修改稿 ID'))
  return request(`/meme-edit-revisions/${encodeURIComponent(id)}`, { method: 'GET' }).then((response) =>
    dataOf(response, '获取修改稿详情失败')
  )
}

/** 中文注释：提交管理员对修改稿的审核决定。 */
export function reviewAdminMemeEditRevision(revisionId, status, note = '') {
  const id = revisionId != null ? String(revisionId).trim() : '' // 中文注释：修改稿审核记录 ID。
  if (!/^\d+$/.test(id)) return Promise.reject(new Error('缺少有效的修改稿 ID'))
  if (!['APPROVED', 'REJECTED'].includes(status)) return Promise.reject(new Error('无效的审核决定'))
  return request(`/meme-edit-revisions/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: { status, note: String(note || '').trim() },
  }).then((response) => dataOf(response, '提交审核结果失败'))
}
