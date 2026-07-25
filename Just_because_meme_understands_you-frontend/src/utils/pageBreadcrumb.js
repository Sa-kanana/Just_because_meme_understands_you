/**
 * 全站面包屑：按「逻辑操作路径」解析，入口用 helper 写 query 溯源。
 *
 * from taxonomy（进详情 / 作为 detailFrom）:
 *   feed | hot | banner | home | search | published | favorite | profile | ai
 *
 * 进个人主页 / 工具页:
 *   from=detail (+ memeId/memeName + detailFrom*)
 *   from=search (+ keyword)
 *   from=profile (+ userId/profileName)
 *   from=home | header | settings | help | ai
 *
 * @typedef {Object} BreadcrumbItem
 * @property {string} label
 * @property {import('vue-router').RouteLocationRaw} [to]
 * @property {boolean} [current]
 * @property {string} [badge]
 * @property {boolean} [loading]
 * @property {boolean} [isHome]
 */

const DEFAULT_MAX_LABEL = 28

const HIDDEN_ROUTE_NAMES = new Set(['home', 'notFound'])

/** 首页系来源：根已是「主页」，不再插中间段 */
const HOME_LIKE_FROM = new Set(['home', 'feed', 'banner', 'header'])

/** 个人主页 / 工具页共用的溯源键 */
const ORIGIN_QUERY_KEYS = [
  'from',
  'keyword',
  'userId',
  'profileName',
  'memeId',
  'memeName',
  'detailFrom',
  'detailKeyword',
  'detailUserId',
  'detailProfileName',
]

export function truncateBreadcrumbLabel(label, max = DEFAULT_MAX_LABEL) {
  const text = String(label || '').trim()
  if (!text) return ''
  if (text.length <= max) return text
  return `${text.slice(0, max)}…`
}

/**
 * @param {ReturnType<typeof import('@/stores/auth').useAuthStore> | null | undefined} authStore
 */
export function resolveAuthUserId(authStore) {
  const user = authStore?.currentUser
  const rawId = user?.id ?? user?.userId
  const userId = rawId != null ? String(rawId).trim() : ''
  return /^\d+$/.test(userId) ? userId : ''
}

function homeItem() {
  return { label: '主页', to: { name: 'home' }, isHome: true }
}

function markCurrent(items) {
  const list = (items || []).filter((it) => it && it.label)
  if (!list.length) return []
  return list.map((it, index) => ({
    ...it,
    current: index === list.length - 1,
  }))
}

function pickOriginFields(query = {}, keys = ORIGIN_QUERY_KEYS) {
  /** @type {Record<string, string>} */
  const out = {}
  for (const key of keys) {
    const val = query[key]
    if (val == null || val === '') continue
    out[key] = String(val)
  }
  return out
}

/**
 * 根据 from 插入可点击中间段（不含最终当前页）。
 * @param {BreadcrumbItem[]} items
 * @param {string} from
 * @param {Object} [ctx]
 * @param {string} [ctx.keyword]
 * @param {string} [ctx.userId]
 * @param {string} [ctx.profileName]
 * @param {ReturnType<typeof import('@/stores/auth').useAuthStore>} [ctx.authStore]
 */
export function appendNavigationTrail(items, from, ctx = {}) {
  const source = String(from || '').trim()
  if (!source || HOME_LIKE_FROM.has(source)) return

  if (source === 'hot') {
    items.push({ label: '今日热梗', to: { name: 'home' } })
    return
  }

  if (source === 'search') {
    const keyword = String(ctx.keyword || '').trim()
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
    return
  }

  if (source === 'published') {
    const userId = resolveAuthUserId(ctx.authStore)
    items.push({
      label: '我的发布',
      to: userId
        ? { name: 'userProfile', params: { userId }, query: { tab: 'published' } }
        : undefined,
    })
    return
  }

  if (source === 'favorite') {
    const userId = resolveAuthUserId(ctx.authStore) || String(ctx.userId || '').trim()
    items.push({
      label: '我的收藏',
      to: userId
        ? { name: 'userProfile', params: { userId }, query: { tab: 'favorite' } }
        : undefined,
    })
    return
  }

  if (source === 'profile') {
    const profileUserId = String(ctx.userId || '').trim()
    if (!profileUserId) return
    items.push({
      label: String(ctx.profileName || '').trim() || '用户主页',
      to: { name: 'userProfile', params: { userId: profileUserId } },
    })
    return
  }

  if (source === 'settings') {
    items.push({ label: '账号设置', to: { name: 'accountSettings' } })
    return
  }

  if (source === 'help') {
    items.push({ label: '帮助文档', to: { name: 'help' } })
    return
  }

  if (source === 'feedback') {
    items.push({ label: '提交反馈', to: { name: 'feedback' } })
    return
  }

  if (source === 'publish') {
    items.push({ label: '发布梗', to: { name: 'publishMeme' } })
    return
  }

  if (source === 'notifications') {
    items.push({ label: '消息中心', to: { name: 'notifications' } })
    return
  }

  if (source === 'ai') {
    items.push({ label: 'AI 搜梗', to: { name: 'aiSearch' } })
  }
}

/**
 * 工具页 / 个人主页：还原「来自详情」时的完整上游（含梗名段）。
 * @param {BreadcrumbItem[]} items
 * @param {Record<string, any>} query
 * @param {ReturnType<typeof import('@/stores/auth').useAuthStore>} authStore
 */
function appendDetailVisitTrail(items, query, authStore) {
  appendNavigationTrail(items, String(query.detailFrom || '').trim(), {
    keyword: query.detailKeyword,
    userId: query.detailUserId,
    profileName: query.detailProfileName,
    authStore,
  })
  const memeId = String(query.memeId || '').trim()
  if (!memeId) return
  items.push({
    label: String(query.memeName || '').trim() || '梗详情',
    to: {
      name: 'memeDetail',
      params: { id: memeId },
      query: rebuildMemeDetailQueryFromOrigin(query),
    },
  })
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
  const items = [homeItem()]

  const from = String(route.query.from || '').trim()
  appendNavigationTrail(items, from, {
    keyword: route.query.keyword,
    userId: route.query.userId,
    profileName: route.query.profileName,
    authStore,
  })

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
    loading,
    badge: ownerPreview
      ? meme?.statusDesc || meme?.status_desc || '预览'
      : undefined,
  })

  return markCurrent(items)
}

/**
 * @param {Object} [options]
 * @param {string} [options.keyword]
 * @param {string} [options.from]
 * @param {string} [options.memeId]
 * @param {string} [options.memeName]
 * @param {string} [options.detailFrom]
 * @param {string} [options.detailKeyword]
 * @param {string} [options.detailUserId]
 * @param {string} [options.detailProfileName]
 * @param {ReturnType<typeof import('@/stores/auth').useAuthStore>} [options.authStore]
 */
export function buildSearchBreadcrumbs(options = {}) {
  const keyword = String(options.keyword || '').trim()
  const from = String(options.from || '').trim()
  /** @type {BreadcrumbItem[]} */
  const items = [homeItem()]

  if (from === 'detail' && options.memeId) {
    appendDetailVisitTrail(items, options, options.authStore)
  }

  items.push({
    label: '搜索',
    to: { name: 'search' },
  })
  if (keyword) {
    items.push({ label: keyword })
  }
  return markCurrent(items)
}

/**
 * @param {Record<string, any>} [query]
 * @param {ReturnType<typeof import('@/stores/auth').useAuthStore>} [authStore]
 */
export function buildHelpBreadcrumbs(query = {}, authStore) {
  const items = [homeItem()]
  appendToolOrigin(items, query, authStore)
  items.push({ label: '帮助文档' })
  return markCurrent(items)
}

function appendToolOrigin(items, query, authStore) {
  const from = String(query.from || '').trim()
  if (from === 'detail') {
    appendDetailVisitTrail(items, query, authStore)
    return
  }
  appendNavigationTrail(items, from, {
    keyword: query.keyword,
    userId: query.userId,
    profileName: query.profileName,
    authStore,
  })
}

/**
 * 根据当前路由 + 可选动态补丁生成面包屑。
 * @param {import('vue-router').RouteLocationNormalizedLoaded} route
 * @param {Object} [context]
 * @param {ReturnType<typeof import('@/stores/auth').useAuthStore>} [context.authStore]
 * @param {Record<string, any>} [context.patch]
 * @returns {BreadcrumbItem[]}
 */
export function resolveRouteBreadcrumbs(route, context = {}) {
  if (!route || route.meta?.hideBreadcrumb === true) return []
  const name = route.name != null ? String(route.name) : ''
  if (!name || HIDDEN_ROUTE_NAMES.has(name)) return []
  if (route.matched?.some((r) => r.meta?.hideBreadcrumb === true)) return []

  const authStore = context.authStore
  const patch = context.patch && typeof context.patch === 'object' ? context.patch : {}

  switch (name) {
    case 'search':
      return buildSearchBreadcrumbs({
        keyword: route.query.keyword || patch.keyword || '',
        from: route.query.from,
        memeId: route.query.memeId,
        memeName: route.query.memeName,
        detailFrom: route.query.detailFrom,
        detailKeyword: route.query.detailKeyword,
        detailUserId: route.query.detailUserId,
        detailProfileName: route.query.detailProfileName,
        authStore,
      })
    case 'help':
      return buildHelpBreadcrumbs(route.query, authStore)
    case 'feedback': {
      const items = [homeItem()]
      appendToolOrigin(items, route.query, authStore)
      items.push({ label: '提交反馈' })
      return markCurrent(items)
    }
    case 'memeDetail':
      return buildMemeDetailBreadcrumbs({
        route,
        meme: patch.meme || null,
        loading: !!patch.loading,
        ownerPreview: !!patch.ownerPreview,
        authStore,
        error: patch.error || '',
      })
    case 'publishMeme': {
      const items = [homeItem()]
      appendToolOrigin(items, route.query, authStore)
      items.push({ label: '发布梗' })
      return markCurrent(items)
    }
    case 'accountSettings': {
      const tab = String(route.query.tab || patch.tab || '').trim()
      const items = [homeItem()]
      appendToolOrigin(items, route.query, authStore)
      items.push({
        label: '账号设置',
        to: {
          name: 'accountSettings',
          query: pickOriginFields(route.query),
        },
      })
      if (tab === 'security') {
        items.push({ label: '账号安全' })
      } else if (tab === 'profile') {
        items.push({ label: '个人资料' })
      }
      return markCurrent(items)
    }
    case 'notifications': {
      const items = [homeItem()]
      appendToolOrigin(items, route.query, authStore)
      items.push({ label: '消息中心' })
      return markCurrent(items)
    }
    case 'aiSearch': {
      const items = [homeItem()]
      appendToolOrigin(items, route.query, authStore)
      items.push({ label: 'AI 搜梗' })
      return markCurrent(items)
    }
    case 'userProfile': {
      const userId = String(route.params.userId || '').trim()
      const selfId = resolveAuthUserId(authStore)
      const resolvedUserId = userId === 'me' && selfId ? selfId : userId
      const isSelf = userId === 'me' || (selfId && userId === selfId)
      const nickname = String(patch.nickname || '').trim()
      const label = nickname || (isSelf ? '个人主页' : '用户主页')
      const originQuery = pickUserProfileOriginQuery(route.query)
      const items = [homeItem()]

      const from = String(route.query.from || '').trim()
      if (from === 'detail') {
        appendDetailVisitTrail(items, route.query, authStore)
      } else {
        appendNavigationTrail(items, from, {
          keyword: route.query.keyword,
          userId: route.query.userId,
          profileName: route.query.profileName,
          authStore,
        })
      }

      const profileIndex = items.length
      items.push({ label, loading: !!patch.loading })
      const tab = String(route.query.tab || '').trim()
      if (tab === 'published' || tab === 'favorite') {
        items[profileIndex].to = resolvedUserId
          ? {
              name: 'userProfile',
              params: { userId: resolvedUserId },
              query: { ...originQuery },
            }
          : undefined
        items[profileIndex].loading = false
        items.push({
          label:
            tab === 'published'
              ? isSelf
                ? '我的发布'
                : '发布'
              : isSelf
                ? '我的收藏'
                : '收藏',
        })
      }
      return markCurrent(items)
    }
    case 'login':
      return markCurrent([homeItem(), { label: '登录' }])
    case 'register':
      return markCurrent([homeItem(), { label: '注册' }])
    case 'forgotPassword':
      return markCurrent([homeItem(), { label: '找回密码' }])
    case 'error': {
      const code = String(route.params.code || '500')
      return markCurrent([
        homeItem(),
        { label: code === '404' ? '页面不存在' : `错误 ${code}` },
      ])
    }
    default: {
      const label = String(route.meta?.breadcrumbLabel || route.meta?.title || '').trim()
      if (!label) return []
      return markCurrent([homeItem(), { label }])
    }
  }
}

/**
 * 构建进入梗详情页时携带的来源 query。
 * @param {Object} [options]
 * @param {string} [options.from]
 * @param {string} [options.keyword]
 * @param {string|number} [options.userId]
 * @param {string} [options.profileName]
 */
export function buildMemeDetailNavQuery(options = {}) {
  /** @type {Record<string, string>} */
  const query = {}
  const from = options.from != null ? String(options.from).trim() : ''
  if (from) query.from = from
  const keyword = options.keyword != null ? String(options.keyword).trim() : ''
  if (keyword) query.keyword = keyword
  if (options.userId != null && String(options.userId).trim()) {
    query.userId = String(options.userId).trim()
  }
  const profileName = options.profileName != null ? String(options.profileName).trim() : ''
  if (profileName) query.profileName = profileName
  return query
}

/**
 * @param {string|number} id
 * @param {Object} [options] 同 buildMemeDetailNavQuery
 */
export function buildMemeDetailLocation(id, options = {}) {
  return {
    name: 'memeDetail',
    params: { id: String(id) },
    query: buildMemeDetailNavQuery(options),
  }
}

/**
 * 从 query 中取出溯源字段（不含 tab）。
 * @param {Record<string, any>} query
 */
export function pickUserProfileOriginQuery(query = {}) {
  return pickOriginFields(query)
}

/**
 * 从当前页构建个人主页跳转 location。
 * @param {string|number} userId
 * @param {Object} [options]
 * @param {import('vue-router').RouteLocationNormalizedLoaded} [options.fromRoute]
 * @param {string} [options.memeName]
 * @param {string} [options.profileName]
 * @param {string} [options.tab]
 * @param {string} [options.from]
 * @param {string} [options.keyword]
 * @param {Record<string, any>} [options.query]
 */
export function buildUserProfileLocation(userId, options = {}) {
  const uid = userId != null ? String(userId).trim() : ''
  /** @type {Record<string, string>} */
  const query = {
    ...(options.query && typeof options.query === 'object' ? options.query : {}),
  }
  if (options.tab) query.tab = String(options.tab)

  const route = options.fromRoute
  if (route && route.name === 'memeDetail') {
    const memeId = String(route.params?.id || route.query?.memeId || '').trim()
    if (memeId) {
      query.from = 'detail'
      query.memeId = memeId
      const memeName = String(options.memeName || '').trim()
      if (memeName) query.memeName = memeName.slice(0, 40)
      copyDetailUpstreamIntoProfileQuery(query, route.query)
    }
  } else if (route && route.name === 'search') {
    query.from = 'search'
    const keyword = String(options.keyword || route.query?.keyword || '').trim()
    if (keyword) query.keyword = keyword
  } else if (route && route.name === 'userProfile') {
    const profileUserId = String(route.params?.userId || '').trim()
    if (profileUserId && profileUserId !== 'me' && profileUserId !== uid) {
      query.from = 'profile'
      query.userId = profileUserId
      const profileName = String(options.profileName || '').trim()
      if (profileName) query.profileName = profileName
    }
  } else if (options.from) {
    query.from = String(options.from).trim()
    const keyword = String(options.keyword || '').trim()
    if (keyword) query.keyword = keyword
  } else if (route && (route.name === 'home' || !route.name)) {
    query.from = 'home'
  }

  return {
    name: 'userProfile',
    params: { userId: uid },
    query,
  }
}

/**
 * 从当前路由构建搜索页 location。
 * @param {Object} [options]
 * @param {string} [options.keyword]
 * @param {import('vue-router').RouteLocationNormalizedLoaded} [options.fromRoute]
 * @param {string} [options.memeName]
 * @param {string} [options.from]
 * @param {string} [options.focus]
 */
export function buildSearchLocation(options = {}) {
  /** @type {Record<string, string>} */
  const query = {}
  const keyword = options.keyword != null ? String(options.keyword).trim() : ''
  if (keyword) query.keyword = keyword
  if (options.focus) query.focus = String(options.focus)

  const route = options.fromRoute
  if (route && route.name === 'memeDetail') {
    const memeId = String(route.params?.id || '').trim()
    if (memeId) {
      query.from = 'detail'
      query.memeId = memeId
      const memeName = String(options.memeName || '').trim()
      if (memeName) query.memeName = memeName.slice(0, 40)
      copyDetailUpstreamIntoProfileQuery(query, route.query)
    }
  } else if (options.from) {
    query.from = String(options.from).trim()
  } else if (route && route.name === 'home') {
    query.from = 'home'
  } else if (route && route.name === 'userProfile') {
    query.from = 'profile'
    const userId = String(route.params?.userId || '').trim()
    if (userId && userId !== 'me') query.userId = userId
    const profileName = String(options.profileName || '').trim()
    if (profileName) query.profileName = profileName
  } else if (!query.from) {
    query.from = 'header'
  }

  return { name: 'search', query }
}

/**
 * 发布 / 设置 / 帮助等工具页，带上进入前的逻辑路径。
 * @param {'publishMeme'|'accountSettings'|'help'|'notifications'|'aiSearch'|'feedback'} name
 * @param {Object} [options]
 * @param {import('vue-router').RouteLocationNormalizedLoaded} [options.fromRoute]
 * @param {string} [options.memeName]
 * @param {string} [options.profileName]
 * @param {string} [options.from]
 * @param {Record<string, any>} [options.query]
 */
export function buildToolPageLocation(name, options = {}) {
  /** @type {Record<string, string>} */
  const query = {
    ...(options.query && typeof options.query === 'object' ? options.query : {}),
  }
  const route = options.fromRoute

  if (!route || route.name === 'home' || route.name === name) {
    if (options.from && !query.from) query.from = String(options.from).trim()
    return { name, query }
  }

  if (route.name === 'memeDetail') {
    const memeId = String(route.params?.id || '').trim()
    if (memeId) {
      query.from = 'detail'
      query.memeId = memeId
      const memeName = String(options.memeName || '').trim()
      if (memeName) query.memeName = memeName.slice(0, 40)
      copyDetailUpstreamIntoProfileQuery(query, route.query)
    }
  } else if (route.name === 'search') {
    query.from = 'search'
    const keyword = String(route.query?.keyword || '').trim()
    if (keyword) query.keyword = keyword
  } else if (route.name === 'userProfile') {
    query.from = 'profile'
    const userId = String(route.params?.userId || '').trim()
    if (userId && userId !== 'me') query.userId = userId
    const profileName = String(options.profileName || '').trim()
    if (profileName) query.profileName = profileName
  } else if (route.name === 'accountSettings') {
    query.from = 'settings'
  } else if (route.name === 'help') {
    query.from = 'help'
  } else if (route.name === 'feedback') {
    query.from = 'feedback'
  } else if (route.name === 'publishMeme') {
    query.from = 'publish'
  } else if (route.name === 'notifications') {
    query.from = 'notifications'
  } else if (route.name === 'aiSearch') {
    query.from = 'ai'
  } else if (options.from) {
    query.from = String(options.from).trim()
  }

  return { name, query }
}

/**
 * 把详情页已有的 from 链写入个人主页 / 工具页 / 搜索的 detail* 字段。
 * @param {Record<string, string>} target
 * @param {Record<string, any>} detailQuery
 */
function copyDetailUpstreamIntoProfileQuery(target, detailQuery = {}) {
  const detailFrom = String(detailQuery.from || '').trim()
  if (!detailFrom) return
  target.detailFrom = detailFrom
  const keyword = String(detailQuery.keyword || '').trim()
  if (keyword) target.detailKeyword = keyword
  const profileUserId = String(detailQuery.userId || '').trim()
  if (profileUserId) target.detailUserId = profileUserId
  const profileName = String(detailQuery.profileName || '').trim()
  if (profileName) target.detailProfileName = profileName
}

/**
 * 还原详情页自身的 from 溯源。
 * @param {Record<string, any>} query
 */
function rebuildMemeDetailQueryFromOrigin(query = {}) {
  /** @type {Record<string, string>} */
  const detailQuery = {}
  const detailFrom = String(query.detailFrom || '').trim()
  if (detailFrom) detailQuery.from = detailFrom
  const keyword = String(query.detailKeyword || '').trim()
  if (keyword) detailQuery.keyword = keyword
  const profileUserId = String(query.detailUserId || '').trim()
  if (profileUserId) detailQuery.userId = profileUserId
  const profileName = String(query.detailProfileName || '').trim()
  if (profileName) detailQuery.profileName = profileName
  return detailQuery
}

