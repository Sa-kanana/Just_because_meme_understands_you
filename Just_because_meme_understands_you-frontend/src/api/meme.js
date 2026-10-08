/**
 * 梗的分页展示接口
 * 接口定义：GET /memes?page=  （每次返回 8 个梗元素）
 * @see https://api.apifox.com/temp-links/api/423205377
 */
import { request } from './request'
import { getViewSessionId } from '@/utils/viewSession'
import { normalizePageMetadata } from '@/utils/pageMetadata.mjs'

function normalizeFolderId(value) {
  if (value == null || value === '') return '0'
  return String(value).trim()
}

/**
 * @typedef {Object} MemeTag
 * @property {number} id
 * @property {string} name
 * @property {number|string} [relatedQuantity]
 */

/**
 * @typedef {Object} MemeItem
 * @property {number} id
 * @property {string} name
 * @property {string} image
 * @property {number} pageViews
 * @property {number} likes
 * @property {number} comments
 * @property {string} [releaseTime]
 * @property {string} [updateTime]
 * @property {number} status - 状态（1.正常，2.审核中，3.下架）
 * @property {MemeTag[]} [memeTag] - 接口返回的标签字段
 * @property {MemeTag[]} [label] - 兼容老字段，前端展示标签时优先使用
 */

/**
 * 获取梗分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {string} [params.sort='hot'] hot | new | comments | views | following | tags
 * @param {number} [params.size=16]
 * @returns {Promise<{ list: MemeItem[], page: number, size: number, total: number, hasMore: boolean }>}
 */
function normalizeMemeItems(items) {
  if (!Array.isArray(items)) return []
  return items
    .filter((item) => item && Number(item.status) === 1)
    .map((item) => {
      const label = item.label || item.memeTag || []
      return { ...item, label }
    })
}

export function getMemeList(params = {}) {
  const { page = 1, sort = 'hot', size = 16 } = params
  const query = new URLSearchParams({
    page: String(page),
    sort: String(sort),
    size: String(size),
  })
  return request(`/memes?${query}`, { method: 'GET' }).then((res) => {
    if (res && res.data && typeof res.data === 'object') {
      const data = res.data
      if (Array.isArray(data.list)) {
        const metadata = normalizePageMetadata(data, { page, size })
        return {
          list: normalizeMemeItems(data.list),
          ...metadata,
        }
      }
      if (Array.isArray(data)) {
        const list = normalizeMemeItems(data)
        const metadata = normalizePageMetadata(null, {
          page,
          size,
          total: list.length,
          hasMore: list.length >= Number(size),
        })
        return {
          list,
          ...metadata,
        }
      }
    }
    if (res && Array.isArray(res.data)) {
      const list = normalizeMemeItems(res.data)
      const metadata = normalizePageMetadata(null, {
        page,
        size,
        total: list.length,
        hasMore: list.length >= Number(size),
      })
      return {
        list,
        ...metadata,
      }
    }
    return { list: [], ...normalizePageMetadata(null, { page, size }) }
  })
}

/**
 * 详情接口返回的梗对象结构
 *
 * 对应接口文档中的 Meme：
 * https://api.apifox.com/temp-links/api/425006824
 *
 * @typedef {Object} MemeDetail
 * @property {number} id - 唯一标识
 * @property {string} [introduction] - 介绍
 * @property {string} [name]
 * @property {string} [image]
 * @property {number} [pageViews]
 * @property {number} [likes]
 * @property {number} [comments]
 * @property {string} [releaseTime]
 * @property {string} [updateTime]
 * @property {number} status - 状态（1.正常，2.审核中，3.下架）
 * @property {MemeTag[]} [memeTag] - 标签
 * @property {MemeResource[]} [links] - 相关链接
 */

/**
 * @typedef {Object} MemeResource
 * @property {number} id
 * @property {string} type - link / media / video / article / image
 * @property {string} [title]
 * @property {string} url
 * @property {number} [sortOrder]
 */

/**
 * 获取单个梗的详细信息
 * 接口定义：GET /memes/{memeId}（梗的详细页面）
 * @see https://api.apifox.com/temp-links/api/425006824
 *
 * @param {number|string} memeId - 梗的唯一 id
 * @returns {Promise<MemeDetail>}
 */
export function getMemeDetail(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}`, { method: 'GET' }).then((res) => {
    // 接口文档：DataRespose<Meme>，{ code, message, data: { ...meme } }
    if (res && res.data && typeof res.data === 'object') {
      return res.data
    }
    const msg = res && (res.message || res.msg)
    throw new Error(msg || '未获取到梗详情数据')
  })
}

/**
 * 收藏梗
 * PUT /users/me/favorites/{memeId}
 * @param {number|string} memeId
 * @param {number} [folderId=0]
 */
export function addMemeFavorite(memeId, folderId = 0) {
  const id = memeId != null ? Number(memeId) : NaN
  if (!Number.isFinite(id) || id <= 0) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/users/me/favorites/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    body: JSON.stringify({ folderId: normalizeFolderId(folderId) }),
  }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '收藏失败')
  })
}

/**
 * 取消收藏梗
 * DELETE /users/me/favorites/{memeId}
 * @param {number|string} memeId
 */
export function removeMemeFavorite(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/users/me/favorites/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '取消收藏失败')
  })
}

/**
 * 上报浏览
 * POST /meme-views
 * @param {number|string} memeId
 * @param {{ source?: 'detail'|'list'|'search'|'share' }} [options]
 * @returns {Promise<{ memeId: number, pageViews: number, counted?: boolean }>}
 */
export function reportMemeView(memeId, options = {}) {
  const id = memeId != null ? Number(memeId) : NaN
  if (!Number.isFinite(id) || id <= 0) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  const source = options.source != null ? String(options.source).trim() : 'detail'
  return request('/meme-views', {
    method: 'POST',
    body: JSON.stringify({ memeId: id, source }),
    headers: {
      'X-View-Session-Id': getViewSessionId(),
    },
  }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      return {
        memeId: res.data.memeId != null ? Number(res.data.memeId) : id,
        pageViews: res.data.pageViews != null ? Number(res.data.pageViews) : 0,
        counted: res.data.counted != null ? !!res.data.counted : undefined,
      }
    }
    throw new Error((res && (res.message || res.msg)) || '上报浏览失败')
  })
}

/**
 * 查询单个梗浏览量
 * GET /memes/{memeId}/views/count
 */
export function getMemeViewCount(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}/views/count`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      return {
        memeId: res.data.memeId != null ? Number(res.data.memeId) : Number(id),
        pageViews: res.data.pageViews != null ? Number(res.data.pageViews) : 0,
      }
    }
    throw new Error((res && (res.message || res.msg)) || '查询浏览量失败')
  })
}

/**
 * 批量查询浏览量
 * GET /meme-view-counts?memeIds=1,2,3
 */
export function batchMemeViewCounts(memeIds) {
  const ids = (Array.isArray(memeIds) ? memeIds : [])
    .map((id) => Number(id))
    .filter((id) => Number.isFinite(id) && id > 0)
  if (!ids.length) {
    return Promise.resolve({ items: [] })
  }
  const query = new URLSearchParams({ memeIds: ids.join(',') })
  return request(`/meme-view-counts?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      const items = Array.isArray(res.data.items) ? res.data.items : []
      return {
        items: items.map((item) => ({
          memeId: item?.memeId != null ? Number(item.memeId) : undefined,
          pageViews: item?.pageViews != null ? Number(item.pageViews) : 0,
        })),
      }
    }
    throw new Error((res && (res.message || res.msg)) || '批量查询浏览量失败')
  })
}

/**
 * 点赞梗
 * PUT /users/me/liked-memes/{memeId}
 * @param {number|string} memeId
 * @returns {Promise<{ likeId?: number, memeId: number, liked: boolean, likeCount: number, createTime?: string }>}
 */
export function addMemeLike(memeId) {
  const id = memeId != null ? Number(memeId) : NaN
  if (!Number.isFinite(id) || id <= 0) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/users/me/liked-memes/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
  }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '点赞失败')
  })
}

/**
 * 取消点赞
 * DELETE /users/me/liked-memes/{memeId}
 */
export function removeMemeLike(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/users/me/liked-memes/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '取消点赞失败')
  })
}

/**
 * 查询当前用户是否已点赞
 * GET /users/me/liked-memes/{memeId}
 */
export function getMemeLikeStatus(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/users/me/liked-memes/${encodeURIComponent(id)}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      return {
        liked: !!res.data.liked,
        likeCount: res.data.likeCount != null ? Number(res.data.likeCount) : 0,
      }
    }
    throw new Error((res && (res.message || res.msg)) || '查询点赞状态失败')
  })
}

/**
 * 批量查询点赞状态
 * GET /users/me/liked-memes?memeIds=1,2,3
 */
export function batchMemeLikeStatus(memeIds) {
  const ids = (Array.isArray(memeIds) ? memeIds : [])
    .map((id) => Number(id))
    .filter((id) => Number.isFinite(id) && id > 0)
  if (!ids.length) {
    return Promise.resolve({ items: [] })
  }
  const query = new URLSearchParams({ memeIds: ids.join(',') })
  return request(`/users/me/liked-memes?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      const items = Array.isArray(res.data.items) ? res.data.items : []
      return {
        items: items.map((item) => ({
          memeId: item?.memeId != null ? Number(item.memeId) : undefined,
          liked: !!item?.liked,
        })),
      }
    }
    throw new Error((res && (res.message || res.msg)) || '批量查询点赞状态失败')
  })
}

/**
 * 查询当前用户是否已收藏该梗
 * GET /users/me/favorites/{memeId}
 * @param {number|string} memeId
 * @returns {Promise<{ favorited: boolean, folderId?: number, sortOrder?: number }>}
 */
export function getMemeFavoriteStatus(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/users/me/favorites/${encodeURIComponent(id)}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data && typeof res.data === 'object') {
      return {
        favorited: !!res.data.favorited,
        folderId: res.data.folderId != null ? normalizeFolderId(res.data.folderId) : undefined,
        sortOrder: res.data.sortOrder != null ? Number(res.data.sortOrder) : undefined,
      }
    }
    throw new Error((res && (res.message || res.msg)) || '查询收藏状态失败')
  })
}

/**
 * 单条移动到目标夹
 * PATCH /users/me/favorites/{memeId}，请求体包含 targetFolderId
 */
export function moveMemeFavorite(memeId, folderId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少梗 id'))
  const fid = normalizeFolderId(folderId)
  return request(`/users/me/favorites/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify({ targetFolderId: fid }),
  }).then((res) => {
    if (res && Number(res.code) === 1) return res.data || {}
    throw new Error((res && (res.message || res.msg)) || '移动收藏失败')
  })
}

/**
 * 批量移动收藏到目标夹
 * PATCH /users/me/favorites
 */
export function batchMoveFavorites(targetFolderId, memeIds) {
  return request('/users/me/favorites', {
    method: 'PATCH',
    body: JSON.stringify({
      targetFolderId: normalizeFolderId(targetFolderId),
      memeIds: Array.isArray(memeIds) ? memeIds : [],
    }),
  }).then((res) => {
    if (res && Number(res.code) === 1) return res.data || { movedCount: 0, failed: [] }
    throw new Error((res && (res.message || res.msg)) || '批量移动失败')
  })
}

/**
 * 夹内收藏排序
 * PUT /users/me/favorites/order
 */
export function reorderFavorites(folderId, favoriteIds) {
  return request('/users/me/favorites/order', {
    method: 'PUT',
    body: JSON.stringify({
      folderId: normalizeFolderId(folderId),
      favoriteIds: Array.isArray(favoriteIds) ? favoriteIds : [],
    }),
  }).then((res) => {
    if (res && Number(res.code) === 1) return true
    throw new Error((res && (res.message || res.msg)) || '排序失败')
  })
}

/**
 * 分页获取根评论
 * GET /memes/{memeId}/comments
 */
export function getMemeRootComments(memeId, params = {}) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少梗 id'))
  const { page = 1, size = 10, sortType = 'new' } = params
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
    sortType: String(sortType),
  })
  return request(`/memes/${encodeURIComponent(id)}/comments?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      return res.data
    }
    throw new Error((res && (res.message || res.msg)) || '加载评论失败')
  })
}

/**
 * 分页获取子评论
 * GET /comments/{rootId}/replies
 * @returns {Promise<{list: Array, page: number, size: number, total: number, hasMore: boolean}>}
 */
export function getMemeCommentReplies(rootId, params = {}) {
  const id = rootId != null ? String(rootId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少根评论 id'))
  const { page = 1, size = 10 } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/comments/${encodeURIComponent(id)}/replies?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      // 兼容旧版直接返回数组
      if (Array.isArray(res.data)) {
        return {
          list: res.data,
          page: Number(page) || 1,
          size: Number(size) || 10,
          total: res.data.length,
          hasMore: false,
        }
      }
      return {
        list: Array.isArray(res.data.list) ? res.data.list : [],
        page: Number(res.data.page) || Number(page) || 1,
        size: Number(res.data.size) || Number(size) || 10,
        total: Number(res.data.total) || 0,
        hasMore: Boolean(res.data.hasMore),
      }
    }
    throw new Error((res && (res.message || res.msg)) || '加载回复失败')
  })
}

/**
 * 评论定位（消息跳转）
 * GET /comments/{commentId}/context
 */
export function getMemeCommentAnchor(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/comments/${encodeURIComponent(id)}/context`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      const data = res.data
      return {
        memeId: data.memeId != null ? String(data.memeId) : '',
        commentId: data.commentId != null ? String(data.commentId) : id,
        rootId: data.rootId != null ? String(data.rootId) : '',
        parentId: data.parentId != null ? String(data.parentId) : '',
        root: Boolean(data.root),
      }
    }
    throw new Error((res && (res.message || res.msg)) || '定位评论失败')
  })
}

/**
 * 发表评论（根评论 / 回复子评论）
 * POST /memes/{memeId}/comments
 * @param {Object} payload
 * @param {number|string} payload.memeId
 * @param {string} [payload.rootId='0']
 * @param {string} [payload.parentId='0']
 * @param {string} payload.content
 * @param {string[]} [payload.imageUrls=[]]
 */
export function addMemeComment(payload = {}) {
  const memeId = payload.memeId != null ? Number(payload.memeId) : NaN
  if (!Number.isFinite(memeId) || memeId <= 0) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  const body = {
    rootId: payload.rootId != null ? String(payload.rootId) : '0',
    parentId: payload.parentId != null ? String(payload.parentId) : '0',
    content: String(payload.content || '').trim(),
    imageUrls: Array.isArray(payload.imageUrls) ? payload.imageUrls : [],
  }
  return request(`/memes/${encodeURIComponent(String(memeId))}/comments`, {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '发表评论失败')
  })
}

/**
 * 删除评论
 * DELETE /comments/{commentId}
 */
export function deleteMemeComment(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/comments/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1) return res.data || true
    throw new Error((res && (res.message || res.msg)) || '删除评论失败')
  })
}

/**
 * 评论点赞
 * PUT /comments/{commentId}/likes/me
 */
export function likeMemeComment(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/comments/${encodeURIComponent(id)}/likes/me`, { method: 'PUT' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '点赞失败')
  })
}

/**
 * 取消评论点赞
 * DELETE /comments/{commentId}/likes/me
 */
export function unlikeMemeComment(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/comments/${encodeURIComponent(id)}/likes/me`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '取消点赞失败')
  })
}

/**
 * 单条评论点赞状态
 * GET /comments/{commentId}/likes/me
 */
export function getMemeCommentLikeStatus(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/comments/${encodeURIComponent(id)}/likes/me`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '查询点赞状态失败')
  })
}

/**
 * 批量评论点赞状态
 * GET /comment-like-statuses?commentIds=1,2,3
 */
export function batchMemeCommentLikeStatus(commentIds = []) {
  const ids = (Array.isArray(commentIds) ? commentIds : [])
    .map((id) => String(id || '').trim())
    .filter(Boolean)
  if (!ids.length) return Promise.resolve({ items: [] })
  const query = new URLSearchParams({ commentIds: ids.join(',') })
  return request(`/comment-like-statuses?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '批量查询点赞状态失败')
  })
}

/**
 * 获取所有可用标签
 * GET /tags
 */
export function getMemeTags() {
  return request('/tags', { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && Array.isArray(res.data)) return res.data
    throw new Error((res && (res.message || res.msg)) || '加载标签失败')
  })
}

/**
 * 发布梗
 * POST /memes
 * @param {Object} payload { name, introduction, image, tagIds:number[], resourceUrls:string[], resources:Array<{type,url,title,sortOrder}> }
 */
export function publishMeme(payload = {}) {
  const resources = Array.isArray(payload.resources)
    ? payload.resources
        .map((item, index) => ({
          type: String(item?.type || 'link').trim() || 'link',
          url: String(item?.url || '').trim(),
          title: String(item?.title || '').trim(),
          sortOrder: Number.isFinite(Number(item?.sortOrder)) ? Number(item.sortOrder) : index,
        }))
        .filter((item) => item.url && item.title)
    : []

  const body = {
    name: String(payload.name || '').trim(),
    introduction: String(payload.introduction || '').trim(),
    image: String(payload.image || '').trim(),
    tagIds: Array.isArray(payload.tagIds) ? payload.tagIds : [],
    resourceUrls: Array.isArray(payload.resourceUrls) ? payload.resourceUrls : [],
    resources,
  }
    return request('/memes', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '发布失败')
  })
}

/**
 * 删除自己发布的梗
 * DELETE /memes/{memeId}
 * @param {number|string} memeId
 * @returns {Promise<{ memeId:number, status:number, statusDesc:string, deletedAt:string }>}
 */
export function deletePublishedMeme(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '删除失败')
  })
}

/**
 * 恢复已下架的梗（重新进入审核）
 * POST /memes/{memeId}/restorations
 * @param {number|string} memeId
 * @returns {Promise<{ memeId:number, status:number, statusDesc:string, restoredAt:string }>}
 */
export function restorePublishedMeme(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}/restorations`, { method: 'POST' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '恢复失败')
  })
}

/**
 * 彻底删除已下架的梗
 * DELETE /memes/{memeId}?permanent=true
 * @param {number|string} memeId
 * @returns {Promise<{ memeId:number, status:number, statusDesc:string, purgedAt:string }>}
 */
export function purgePublishedMeme(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}?permanent=true`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '彻底删除失败')
  })
}
