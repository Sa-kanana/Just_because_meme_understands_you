/**
 * @typedef {Object} BreadcrumbItem
 * @property {string} label
 * @property {import('vue-router').RouteLocationRaw} [to]
 * @property {boolean} [current]
 * @property {string} [badge]
 * @property {boolean} [loading]
 */

const DEFAULT_MAX_LABEL = 28

export function truncateBreadcrumbLabel(label, max = DEFAULT_MAX_LABEL) {
  const text = String(label || '').trim()
  if (!text) return ''
  if (text.length <= max) return text
  return `${text.slice(0, max)}…`
}

/**
 * @param {string|number|undefined|null} rawId
 */
export function resolveAuthUserId(authStore) {
  const user = authStore?.currentUser
  const rawId = user?.id ?? user?.userId
  const userId = rawId != null ? String(rawId).trim() : ''
  return /^\d+$/.test(userId) ? userId : ''
}

/**
 * @param {Object} options
 * @param {import('vue-router').RouteLocationNormalizedLoaded} options.route
 * @param {Object|null|undefined} options.meme
 * @param {boolean} options.loading
 * @param {boolean} options.ownerPreview
 * @param {import('@/stores/auth').useAuthStore} options.authStore
 * @param {string} [options.error]
 * @returns {BreadcrumbItem[]}
 */
export function buildMemeDetailBreadcrumbs(options) {
  const {
    route,
    meme,
    loading = false,
    ownerPreview = false,
    authStore,
    error = '',
  } = options

  /** @type {BreadcrumbItem[]} */
  const items = [{ label: '主页', to: { name: 'home' } }]

  const from = String(route.query.from || '').trim()
  const keyword = String(route.query.keyword || '').trim()
  const profileUserId = String(route.query.userId || '').trim()
  const profileLabel = String(route.query.profileName || '').trim()

  const publishedTrail = from === 'published' || ownerPreview
  if (publishedTrail) {
    const userId = resolveAuthUserId(authStore)
    items.push({
      label: '我的发布',
      to: userId
        ? { name: 'userProfile', params: { userId }, query: { tab: 'published' } }
        : undefined,
    })
  } else if (from === 'favorite') {
    const userId = resolveAuthUserId(authStore) || profileUserId
    items.push({
      label: '我的收藏',
      to: userId
        ? { name: 'userProfile', params: { userId }, query: { tab: 'favorite' } }
        : undefined,
    })
  } else if (from === 'search') {
    items.push({
      label: '搜索',
      to: keyword ? { name: 'search', query: { keyword } } : { name: 'search' },
    })
    if (keyword) {
      items.push({
        label: keyword,
        to: { name: 'search', query: { keyword } },
      })
    }
  } else if (from === 'profile' && profileUserId) {
    items.push({
      label: profileLabel || '用户主页',
      to: { name: 'userProfile', params: { userId: profileUserId } },
    })
  }

  let currentLabel = '梗详情'
  if (loading) {
    currentLabel = '加载中…'
  } else if (error && !meme) {
    currentLabel = '加载失败'
  } else if (meme?.name) {
    currentLabel = String(meme.name)
  }

  items.push({
    label: currentLabel,
    current: true,
    loading,
    badge: ownerPreview
      ? (meme?.statusDesc || meme?.status_desc || '预览')
      : undefined,
  })

  return items
}

/**
 * @param {Object} options
 * @param {string} [options.keyword]
 * @returns {BreadcrumbItem[]}
 */
export function buildSearchBreadcrumbs(options = {}) {
  const keyword = String(options.keyword || '').trim()
  const items = [
    { label: '主页', to: { name: 'home' } },
    { label: '搜索', to: { name: 'search' }, current: !keyword },
  ]
  if (keyword) {
    items[1].current = false
    items.push({ label: keyword, current: true })
  }
  return items
}

/**
 * @returns {BreadcrumbItem[]}
 */
export function buildHelpBreadcrumbs() {
  return [
    { label: '主页', to: { name: 'home' } },
    { label: '帮助文档', current: true },
  ]
}

/**
 * 构建进入梗详情页时携带的来源 query，便于面包屑回退。
 * @param {Object} options
 * @param {'published'|'favorite'|'search'|'profile'} options.from
 * @param {string} [options.keyword]
 * @param {string|number} [options.userId]
 * @param {string} [options.profileName]
 */
export function buildMemeDetailNavQuery(options = {}) {
  const query = { from: options.from }
  if (options.keyword) query.keyword = String(options.keyword).trim()
  if (options.userId != null && String(options.userId).trim()) {
    query.userId = String(options.userId).trim()
  }
  if (options.profileName) query.profileName = String(options.profileName).trim()
  return query
}
