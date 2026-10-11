import { request } from './request'

function dataOf(response) {
  if (response && Number(response.code) === 1) return response.data
  throw new Error((response && response.message) || '举报操作失败')
}

export function fetchAdminReports({ page = 1, size = 10, status = '' } = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return request(`/admin/reports?${query}`, { method: 'GET' }).then(dataOf)
}

export function resolveAdminReport(id, result, note = '') {
  return request(`/admin/reports/${encodeURIComponent(String(id))}`, {
    method: 'PATCH',
    body: { result, note },
  }).then(dataOf)
}
