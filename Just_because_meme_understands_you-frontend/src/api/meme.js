/**
 * 梗的分页展示接口
 * 接口定义：GET /list?page=  （每次返回 8 个梗元素）
 * @see https://api.apifox.com/temp-links/api/423205377
 */
import { request } from './request'

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
 * 获取梗分页列表（接口每次返回 8 条）
 * @param {Object} params
 * @param {number} [params.page=1]
 * @returns {Promise<MemeItem[]>}
 */
export function getMemeList(params = {}) {
  const { page = 1 } = params
  const query = new URLSearchParams({ page: String(page) })
  return request(`/list?${query}`, { method: 'GET' }).then((res) => {
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
 * @property {string[]} resourceUrl - 相关链接
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
 * 获取子评论
 * GET /detail/comments/{rootId}/replies
 */
export function getMemeCommentReplies(rootId, params = {}) {
  const id = rootId != null ? String(rootId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少根评论 id'))
  const { page = 1, size = 10 } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/detail/comments/${encodeURIComponent(id)}/replies?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && Array.isArray(res.data)) {
      return res.data
    }
    throw new Error((res && (res.message || res.msg)) || '加载回复失败')
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
 * @param {Object} payload { name, introduction, image, tagIds:number[], resourceUrls:string[] }
 */
export function publishMeme(payload = {}) {
  const body = {
    name: String(payload.name || '').trim(),
    introduction: String(payload.introduction || '').trim(),
    image: String(payload.image || '').trim(),
    tagIds: Array.isArray(payload.tagIds) ? payload.tagIds : [],
    resourceUrls: Array.isArray(payload.resourceUrls) ? payload.resourceUrls : [],
  }
  return request('/memes', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '发布失败')
  })
}
