/**
 * 关键字搜索接口
 * GET /search?keyword=xxx&mostViews=1|mostLikes=1|mostComments=1
 * @see https://api.apifox.com/temp-links/api/424411854
 */
import { request } from './request'

/**
 * @typedef {Object} MemeTag
 * @property {number} id
 * @property {string} name
 * @property {number} [relatedQuantity]
 */

/**
 * @typedef {Object} SimpleMeme
 * @property {number} id
 * @property {string} [name]
 * @property {string} [image]
 * @property {number} [pageViews]
 * @property {number} [likes]
 * @property {number} [comments]
 * @property {string} [releaseTime]
 * @property {string} [updateTime]
 * @property {number} status - 状态（1.正常，2.审核中，3.下架）
 * @property {MemeTag[]} [memeTag] - 接口返回的标签字段
 * @property {MemeTag[]} [label] - 兼容字段，前端展示标签时优先使用
 */

/**
 * 关键字搜索
 * @param {Object} params
 * @param {string} params.keyword - 关键字（必填）
 * @param {string} [params.mostViews] - 最多浏览排序时传 '1'
 * @param {string} [params.mostLikes] - 最多点赞排序时传 '1'
 * @param {string} [params.mostComments] - 最多评论排序时传 '1'
 * @returns {Promise<SimpleMeme[]>}
 */
export function searchMeme(params = {}) {
  const { keyword, mostViews, mostLikes, mostComments } = params
  const kw = keyword != null ? String(keyword).trim() : ''

  // 接口要求 keyword 为必填参数，若缺失则不发起请求
  if (!kw) {
    return Promise.resolve([])
  }

  const query = new URLSearchParams()
  query.set('keyword', kw)
  if (mostViews) query.set('mostViews', mostViews)
  if (mostLikes) query.set('mostLikes', mostLikes)
  if (mostComments) query.set('mostComments', mostComments)
  const qs = query.toString()
  return request(`/search?${qs}`, { method: 'GET' }).then((res) => {
    if (res && Array.isArray(res.data)) {
      // 只展示 status 为 1 的梗，并兼容 memeTag / label 字段
      return res.data
        .filter((item) => item && Number(item.status) === 1)
        .map((item) => {
          const label = item.label || item.memeTag || []
          return { ...item, label }
        })
    }
    return []
  })
}
