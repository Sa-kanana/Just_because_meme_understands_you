import { request } from './request'

/**
 * 收藏夹接口封装
 * 对应后端 /user/me/favorite-folders 与 /user/{userId}/favorite-folders
 */

/** 雪花 ID 必须按字符串处理，避免 JS Number 精度丢失 */
export function normalizeFolderId(value) {
  if (value == null || value === '') return '0'
  return String(value).trim()
}

export function sameFolderId(a, b) {
  return normalizeFolderId(a) === normalizeFolderId(b)
}

function normalizeFolderItem(raw) {
  if (!raw || typeof raw !== 'object') return raw
  return {
    ...raw,
    id: normalizeFolderId(raw.id),
  }
}

function pickFolders(res) {
  if (res && Number(res.code) === 1 && res.data) {
    const folders = Array.isArray(res.data.folders)
      ? res.data.folders.map(normalizeFolderItem)
      : []
    return {
      folders,
      total: res.data.total != null ? Number(res.data.total) : folders.length,
    }
  }
  throw new Error((res && (res.message || res.msg)) || '获取收藏夹失败')
}

/**
 * 获取本人收藏夹列表（含默认夹）
 * GET /user/me/favorite-folders
 */
export function getMyFavoriteFolders() {
  return request('/user/me/favorite-folders', { method: 'GET' }).then(pickFolders)
}

/**
 * 查看他人公开收藏夹列表（他人仅返回公开自定义夹）
 * GET /user/{userId}/favorite-folders
 */
export function getUserFavoriteFolders(userId) {
  const id = userId != null ? String(userId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少用户ID'))
  return request(`/user/${encodeURIComponent(id)}/favorite-folders`, { method: 'GET' }).then(pickFolders)
}

/**
 * 创建收藏夹
 * POST /user/me/favorite-folders
 */
export function createFavoriteFolder(payload = {}) {
  const body = {
    name: String(payload.name || '').trim(),
    description: payload.description != null ? String(payload.description).trim() : '',
    isPublic: payload.isPublic != null ? Number(payload.isPublic) : 1,
  }
  return request('/user/me/favorite-folders', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1) return normalizeFolderItem(res.data || {})
    throw new Error((res && (res.message || res.msg)) || '创建收藏夹失败')
  })
}

/**
 * 更新收藏夹（folderId=0 可更新默认收藏夹元信息，不可删除）
 * PUT /user/me/favorite-folders/{folderId}
 */
export function updateFavoriteFolder(folderId, payload = {}) {
  const id = folderId != null ? String(folderId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少收藏夹 id'))
  const body = {}
  if (payload.name != null) body.name = String(payload.name).trim()
  if (payload.description != null) body.description = String(payload.description).trim()
  if (payload.isPublic != null) body.isPublic = Number(payload.isPublic)
  return request(`/user/me/favorite-folders/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && Number(res.code) === 1) return true
    throw new Error((res && (res.message || res.msg)) || '更新收藏夹失败')
  })
}

/**
 * 删除收藏夹（夹内梗移入默认夹）
 * DELETE /user/me/favorite-folders/{folderId}
 */
export function deleteFavoriteFolder(folderId) {
  const id = folderId != null ? String(folderId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少收藏夹 id'))
  return request(`/user/me/favorite-folders/${encodeURIComponent(id)}`, { method: 'DELETE' }).then((res) => {
    if (res && Number(res.code) === 1) return true
    throw new Error((res && (res.message || res.msg)) || '删除收藏夹失败')
  })
}

/**
 * 收藏夹排序
 * PUT /user/me/favorite-folders/reorder
 */
export function reorderFavoriteFolders(folderIds) {
  return request('/user/me/favorite-folders/reorder', {
    method: 'PUT',
    body: JSON.stringify({
      folderIds: Array.isArray(folderIds) ? folderIds.map(normalizeFolderId).filter((id) => id !== '0') : [],
    }),
  }).then((res) => {
    if (res && Number(res.code) === 1) return true
    throw new Error((res && (res.message || res.msg)) || '排序失败')
  })
}

/**
 * 分页获取用户收藏的梗（按夹）
 * GET /user/{userId}/favorites?folderId=&page=&size=
 */
export function pageUserFavorites(userId, params = {}) {
  const id = userId != null ? String(userId).trim() : ''
  if (!id) return Promise.reject(new Error('缺少用户ID'))
  const { folderId, page = 1, size = 12 } = params
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (folderId != null && folderId !== '') query.set('folderId', normalizeFolderId(folderId))
  return request(`/user/${encodeURIComponent(id)}/favorites?${query}`, { method: 'GET' }).then((res) => {
    if (res && Number(res.code) === 1 && res.data) return res.data
    throw new Error((res && (res.message || res.msg)) || '获取收藏列表失败')
  })
}
