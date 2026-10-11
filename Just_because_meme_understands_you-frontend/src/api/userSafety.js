import { request } from './request'

function unwrap(response) {
  return response?.data ?? response
}

export function getMySuppressions() {
  return request('/users/me/suppressions').then(unwrap)
}

export function getUserSuppressionStatus(targetId) {
  const id = String(targetId ?? '').trim()
  if (!/^\d+$/.test(id)) return Promise.reject(new Error('用户 id 不合法'))
  return request(`/users/me/suppressions/${encodeURIComponent(id)}`).then(unwrap)
}

export function applyUserSuppression(targetId, type) {
  const id = String(targetId ?? '').trim()
  const query = new URLSearchParams({ type: String(type || '') })
  return request(`/users/me/suppressions/${encodeURIComponent(id)}?${query}`, { method: 'PUT' }).then(unwrap)
}

export function removeUserSuppression(targetId, type) {
  const id = String(targetId ?? '').trim()
  const query = new URLSearchParams({ type: String(type || '') })
  return request(`/users/me/suppressions/${encodeURIComponent(id)}?${query}`, { method: 'DELETE' }).then(unwrap)
}
