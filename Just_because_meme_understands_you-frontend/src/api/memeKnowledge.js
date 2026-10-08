import { request } from './request'

function dataOf(response) {
  if (response && Number(response.code) === 1) return response.data
  throw new Error((response && response.message) || '资料卡操作失败')
}

export function getMemeKnowledge(memeId) {
  return request(`/memes/${encodeURIComponent(String(memeId))}/knowledge`, { method: 'GET' }).then(dataOf)
}

export function submitMemeKnowledgeCorrection(memeId, changes) {
  return request(`/memes/${encodeURIComponent(String(memeId))}/knowledge/corrections`, {
    method: 'POST', body: changes,
  }).then(dataOf)
}
