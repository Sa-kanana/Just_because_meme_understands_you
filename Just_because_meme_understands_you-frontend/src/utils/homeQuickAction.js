/**
 * 首页快捷入口导航解析。
 * 优先按 action.key 分发，route 字符串仅作后端/兼容兜底。
 */

export const FAVORITES_REDIRECT_PATH = '/user/me?tab=favorite'
export const FOLLOWING_REDIRECT_PATH = '/?feed=following'

/**
 * @param {Object|null|undefined} action
 * @param {{ isLoggedIn?: boolean, currentUserId?: string }} ctx
 * @returns {{ kind: 'route', route: object|string } | { kind: 'login', redirect: string } | { kind: 'external', url: string } | { kind: 'home-feed', sort: string } | null}
 */
export function resolveQuickActionTarget(action, ctx = {}) {
  if (!action || typeof action !== 'object') return null

  const key = action.key != null ? String(action.key).trim() : ''
  switch (key) {
    case 'publish':
      return { kind: 'route', route: { name: 'publishMeme', query: { from: 'home' } } }
    case 'ai':
      return resolveAiSearchTarget(ctx)
    case 'search':
      return {
        kind: 'route',
        route: { name: 'search', query: { from: 'home', focus: '1' } },
      }
    case 'following':
      return resolveFollowingTarget(ctx)
    case 'favorites':
      return resolveFavoritesTarget(ctx)
    default:
      return resolveLegacyRoute(action.route, ctx)
  }
}

function resolveAiSearchTarget(ctx) {
  const { isLoggedIn = false } = ctx
  if (!isLoggedIn) {
    return { kind: 'login', redirect: '/ai' }
  }
  return {
    kind: 'route',
    route: { name: 'aiSearch', query: { from: 'home' } },
  }
}

function resolveFollowingTarget(ctx) {
  const { isLoggedIn = false } = ctx
  if (!isLoggedIn) {
    return { kind: 'login', redirect: FOLLOWING_REDIRECT_PATH }
  }
  return { kind: 'home-feed', sort: 'following' }
}

function resolveFavoritesTarget(ctx) {
  const { isLoggedIn = false, currentUserId = '' } = ctx
  if (!isLoggedIn || !currentUserId) {
    return { kind: 'login', redirect: FAVORITES_REDIRECT_PATH }
  }
  return {
    kind: 'route',
    route: {
      name: 'userProfile',
      params: { userId: currentUserId },
      query: { tab: 'favorite' },
    },
  }
}

function resolveLegacyRoute(route, ctx) {
  const raw = route != null ? String(route).trim() : ''
  if (!raw) return null

  if (raw.startsWith('http://') || raw.startsWith('https://')) {
    return { kind: 'external', url: raw }
  }

  if (raw.includes('/user/me')) {
    return resolveFavoritesTarget(ctx)
  }

  if (raw.includes('feed=following') || raw === '/?feed=following') {
    return resolveFollowingTarget(ctx)
  }

  if (raw.startsWith('/search')) {
    const query = parseQueryString(raw.split('?')[1] || '')
    return {
      kind: 'route',
      route: {
        name: 'search',
        query: { from: 'home', focus: '1', ...query },
      },
    }
  }

  return { kind: 'route', route: raw }
}

function parseQueryString(queryString) {
  const query = {}
  if (!queryString) return query
  queryString.split('&').forEach((pair) => {
    const [key, value] = pair.split('=')
    if (key) query[key] = decodeURIComponent(value || '')
  })
  return query
}

/**
 * @param {import('vue-router').Router} router
 * @param {{ kind: string, route?: object|string, redirect?: string, url?: string }} target
 */
export function pushQuickActionTarget(router, target) {
  if (!target || !router) return

  if (target.kind === 'external') {
    return target.url
  }

  if (target.kind === 'login') {
    router.push({
      name: 'login',
      query: { redirect: target.redirect || '/' },
    })
    return
  }

  if (target.kind === 'route' && target.route) {
    router.push(target.route)
  }
}