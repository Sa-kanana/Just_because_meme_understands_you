/**
 * 梗的分页展示接口
 * 接口定义：GET /list?page=  （每次返回 8 个梗元素）
 * @see https://api.apifox.com/temp-links/api/423205377
 */
import { request } from './request'
import { getViewSessionId } from '@/utils/viewSession'

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
 * @param {string} [params.sort='hot'] hot | new | comments | views | following
 * @param {number} [params.size=16]
 * @returns {Promise<{ list: MemeItem[], page: number, size: number, hasMore: boolean }>}
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
  return request(`/list?${query}`, { method: 'GET' }).then((res) => {
    if (res && res.data && typeof res.data === 'object') {
      const data = res.data
      if (Array.isArray(data.list)) {
        return {
          list: normalizeMemeItems(data.list),
          page: data.page != null ? Number(data.page) : Number(page),
          size: data.size != null ? Number(data.size) : Number(size),
          hasMore: data.hasMore != null ? !!data.hasMore : false,
        }
      }
      if (Array.isArray(data)) {
        const list = normalizeMemeItems(data)
        return {
          list,
          page: Number(page),
          size: Number(size),
          hasMore: list.length >= Number(size),
        }
      }
    }
    if (res && Array.isArray(res.data)) {
      const list = normalizeMemeItems(res.data)
      return {
        list,
        page: Number(page),
        size: Number(size),
        hasMore: list.length >= Number(size),
      }
    }
    return { list: [], page: Number(page), size: Number(size), hasMore: false }
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
 * 接口定义：GET /detail?memeId= （梗的详细页面）
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
  const query = new URLSearchParams({ memeId: id })
  return request(`/detail?${query}`, { method: 'GET' }).then((res) => {
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
 * POST /detail/favorites
 * @param {number|string} memeId
 * @param {number} [folderId=0]
 */
export function addMemeFavorite(memeId, folderId = 0) {
  const id = memeId != null ? Number(memeId) : NaN
  if (!Number.isFinite(id) || id <= 0) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request('/detail/favorites', {
    method: 'POST',
    body: JSON.stringify({
      memeId: id,
      folderId: normalizeFolderId(folderId),
    }),
  }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '收藏失败')
  })
}

/**
 * 取消收藏梗
 * DELETE /favorites/{memeId}
 * @param {number|string} memeId
 */
export function removeMemeFavorite(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/favorites/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '取消收藏失败')
  })
}

/**
 * 上报浏览
 * POST /detail/views
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
  return request('/detail/views', {
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
 * GET /views/{memeId}/count
 */
export function getMemeViewCount(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/views/${encodeURIComponent(id)}/count`, { method: 'GET' }).then((res) => {
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
 * GET /views/counts?memeIds=1,2,3
 */
export function batchMemeViewCounts(memeIds) {
  const ids = (Array.isArray(memeIds) ? memeIds : [])
    .map((id) => Number(id))
    .filter((id) => Number.isFinite(id) && id > 0)
  if (!ids.length) {
    return Promise.resolve({ items: [] })
  }
  const query = new URLSearchParams({ memeIds: ids.join(',') })
  return request(`/views/counts?${query}`, { method: 'GET' }).then((res) => {
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
 * POST /detail/likes
 * @param {number|string} memeId
 * @returns {Promise<{ likeId?: number, memeId: number, liked: boolean, likeCount: number, createTime?: string }>}
 */
export function addMemeLike(memeId) {
  const id = memeId != null ? Number(memeId) : NaN
  if (!Number.isFinite(id) || id <= 0) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request('/detail/likes', {
    method: 'POST',
    body: JSON.stringify({ memeId: id }),
  }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '点赞失败')
  })
}

/**
 * 取消点赞
 * DELETE /likes/{memeId}
 */
export function removeMemeLike(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/likes/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1) {
      return res.data || {}
    }
    throw new Error((res && (res.message || res.msg)) || '取消点赞失败')
  })
}

/**
 * 查询当前用户是否已点赞
 * GET /likes/{memeId}/status
 */
export function getMemeLikeStatus(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/likes/${encodeURIComponent(id)}/status`, { method: 'GET' }).then((res) => {
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
 * GET /likes/status?memeIds=1,2,3
 */
export function batchMemeLikeStatus(memeIds) {
  const ids = (Array.isArray(memeIds) ? memeIds : [])
    .map((id) => Number(id))
    .filter((id) => Number.isFinite(id) && id > 0)
  if (!ids.length) {
    return Promise.resolve({ items: [] })
  }
  const query = new URLSearchParams({ memeIds: ids.join(',') })
  return request(`/likes/status?${query}`, { method: 'GET' }).then((res) => {
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
 * GET /favorites/{memeId}/status
 * @param {number|string} memeId
 * @returns {Promise<{ favorited: boolean, folderId?: number, sortOrder?: number }>}
 */
export function getMemeFavoriteStatus(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/favorites/${encodeURIComponent(id)}/status`, { method: 'GET' }).then((res) => {
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
 * PUT /user/me/favorites/{memeId}/move?folderId=
 */
export function moveMemeFavorite(memeId, folderId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少梗 id'))
  const fid = normalizeFolderId(folderId)
  const query = new URLSearchParams({ folderId: fid })
  return request(`/user/me/favorites/${encodeURIComponent(id)}/move?${query}`, {
    method: 'PUT',
  }).then((res) => {
    if (res && Number(res.code) === 1) return res.data || {}
    throw new Error((res && (res.message || res.msg)) || '移动收藏失败')
  })
}

/**
 * 批量移动收藏到目标夹
 * PUT /user/me/favorites/move
 */
export function batchMoveFavorites(targetFolderId, memeIds) {
  return request('/user/me/favorites/move', {
    method: 'PUT',
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
 * PUT /user/me/favorites/reorder
 */
export function reorderFavorites(folderId, favoriteIds) {
  return request('/user/me/favorites/reorder', {
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
 * GET /detail/{memeId}/comments
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
  return request(`/detail/${encodeURIComponent(id)}/comments?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) {
      return res.data
    }
    throw new Error((res && (res.message || res.msg)) || '加载评论失败')
  })
}

/**
 * 分页获取子评论
 * GET /detail/comments/{rootId}/replies
 * @returns {Promise<{list: Array, page: number, size: number, total: number, hasMore: boolean}>}
 */
export function getMemeCommentReplies(rootId, params = {}) {
  const id = rootId != null ? String(rootId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少根评论 id'))
  const { page = 1, size = 10 } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/detail/comments/${encodeURIComponent(id)}/replies?${query}`, { method: 'GET' }).then((res) => {
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
 * GET /detail/comments/{commentId}/anchor
 */
export function getMemeCommentAnchor(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/detail/comments/${encodeURIComponent(id)}/anchor`, { method: 'GET' }).then((res) => {
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
 * POST /detail/comments
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
    memeId,
    rootId: payload.rootId != null ? String(payload.rootId) : '0',
    parentId: payload.parentId != null ? String(payload.parentId) : '0',
    content: String(payload.content || '').trim(),
    imageUrls: Array.isArray(payload.imageUrls) ? payload.imageUrls : [],
  }
  return request('/detail/comments', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '发表评论失败')
  })
}

/**
 * 删除评论
 * DELETE /detail/comments/{commentId}
 */
export function deleteMemeComment(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/detail/comments/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '删除评论失败')
  })
}

/**
 * 评论点赞
 * POST /detail/comments/{commentId}/likes
 */
export function likeMemeComment(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/detail/comments/${encodeURIComponent(id)}/likes`, { method: 'POST' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '点赞失败')
  })
}

/**
 * 取消评论点赞
 * DELETE /detail/comments/{commentId}/likes
 */
export function unlikeMemeComment(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/detail/comments/${encodeURIComponent(id)}/likes`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '取消点赞失败')
  })
}

/**
 * 单条评论点赞状态
 * GET /detail/comments/{commentId}/likes/status
 */
export function getMemeCommentLikeStatus(commentId) {
  const id = commentId != null ? String(commentId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少评论 id'))
  return request(`/detail/comments/${encodeURIComponent(id)}/likes/status`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '查询点赞状态失败')
  })
}

/**
 * 批量评论点赞状态
 * GET /detail/comments/likes/status?commentIds=1,2,3
 */
export function batchMemeCommentLikeStatus(commentIds = []) {
  const ids = (Array.isArray(commentIds) ? commentIds : [])
    .map((id) => String(id || '').trim())
    .filter(Boolean)
  if (!ids.length) return Promise.resolve({ items: [] })
  const query = new URLSearchParams({ commentIds: ids.join(',') })
  return request(`/detail/comments/likes/status?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '批量查询点赞状态失败')
  })
}

/**
 * 获取所有可用标签
 * GET /memes/tags
 */
export function getMemeTags() {
  return request('/memes/tags', { method: 'GET' }).then((res) => {
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
 * 恢复已下架的梗
 * GET /memes/{memeId}/restore
 * @param {number|string} memeId
 * @returns {Promise<{ memeId:number, status:number, statusDesc:string, restoredAt:string }>}
 */
export function restorePublishedMeme(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}/restore`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '恢复失败')
  })
}

/**
 * 彻底删除已下架的梗
 * DELETE /memes/{memeId}/purge
 * @param {number|string} memeId
 * @returns {Promise<{ memeId:number, status:number, statusDesc:string, purgedAt:string }>}
 */
export function purgePublishedMeme(memeId) {
  const id = memeId != null ? String(memeId).trim() : ''
  if (!id) {
    return Promise.reject(new Error('缺少梗 id'))
  }
  return request(`/memes/${encodeURIComponent(id)}/purge`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '彻底删除失败')
  })
}
