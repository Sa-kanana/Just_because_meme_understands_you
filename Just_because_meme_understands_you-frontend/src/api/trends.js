import { request } from './request'

export function getMemeTrends(days = 7) {
  const range = Number(days) === 30 ? 30 : 7
  return request(`/trends?days=${range}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '加载热度趋势失败')
  })
}
