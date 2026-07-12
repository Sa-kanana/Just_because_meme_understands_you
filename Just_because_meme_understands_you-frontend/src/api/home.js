/**
 * 首页聚合接口
 * @see https://api.apifox.cn/temp-links/api/485706738
 */
import { request } from './request'

function normalizeMemeItem(item) {
  if (!item || typeof item !== 'object') return null
  if (Number(item.status) !== 1) return null
  const label = item.label || item.memeTag || []
  return { ...item, label }
}

function normalizeMemeList(list) {
  if (!Array.isArray(list)) return []
  return list.map(normalizeMemeItem).filter(Boolean)
}

/**
 * 首页首屏 bootstrap
 * @param {Object} [params]
 * @param {number} [params.hotLimit=6]
 * @param {number} [params.tagLimit=10]
 * @param {string} [params.feedSort='hot']
 * @param {number} [params.feedSize=16]
 */
export function getHomeBootstrap(params = {}) {
  const {
    hotLimit = 6,
    tagLimit = 10,
    feedSort = 'hot',
    feedSize = 16,
  } = params
  const query = new URLSearchParams({
    hotLimit: String(hotLimit),
    tagLimit: String(tagLimit),
    feedSort: String(feedSort),
    feedSize: String(feedSize),
  })
  return request(`/home/bootstrap?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      const data = res.data
      const feed = data.feed && typeof data.feed === 'object' ? data.feed : {}
      return {
        quickActions: Array.isArray(data.quickActions) ? data.quickActions : [],
        hotTags: Array.isArray(data.hotTags) ? data.hotTags : [],
        hotMemes: normalizeMemeList(data.hotMemes),
        feed: {
          sort: feed.sort || feedSort,
          list: normalizeMemeList(feed.list),
          page: feed.page != null ? Number(feed.page) : 1,
          size: feed.size != null ? Number(feed.size) : feedSize,
          hasMore: feed.hasMore != null ? !!feed.hasMore : false,
        },
      }
    }
    throw new Error((res && (res.message || res.msg)) || '加载首页数据失败')
  })
}

/**
 * 热门标签
 * @see https://api.apifox.cn/temp-links/api/485707244
 * @param {number} [limit=10]
 */
export function getHomeHotTags(limit = 10) {
  const query = new URLSearchParams({ limit: String(limit) })
  return request(`/home/hot-tags?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      const tags = Array.isArray(res.data.tags) ? res.data.tags : []
      return tags.filter((tag) => tag && tag.id != null && tag.name)
    }
    throw new Error((res && (res.message || res.msg)) || '加载热门标签失败')
  })
}
