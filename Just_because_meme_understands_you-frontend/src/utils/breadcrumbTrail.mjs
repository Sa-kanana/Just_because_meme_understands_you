const ORIGIN_QUERY_KEYS = new Set([
  'from',
  'userId',
  'profileName',
  'memeId',
  'memeName',
  'detailFrom',
  'detailKeyword',
  'detailUserId',
  'detailProfileName',
])

/** 将路由参数按键名排序，避免参数顺序不同导致同一页面被重复记录。 */
function stableEntries(value = {}, excludedKeys = new Set()) {
  return Object.keys(value || {})
    .filter((key) => !excludedKeys.has(key) && value[key] != null && value[key] !== '')
    .sort()
    .map((key) => [key, value[key]])
}

/** 为页面访问生成稳定标识；来源溯源参数不参与页面身份判断。 */
export function breadcrumbRouteKey(route) {
  const name = route?.name != null ? String(route.name) : ''
  if (!name) return ''
  return [
    name,
    JSON.stringify(stableEntries(route.params)),
    JSON.stringify(stableEntries(route.query, ORIGIN_QUERY_KEYS)),
  ].join('|')
}

function homeVisit() {
  return {
    key: 'home|[]|[]',
    name: 'home',
    label: '主页',
    to: { name: 'home' },
    isHome: true,
  }
}

/** 记录成功访问的页面；再次访问路径中的旧页时截断其后的访问记录。 */
export function recordBreadcrumbVisit(trail, route, currentItem) {
  const name = route?.name != null ? String(route.name) : ''
  if (!name) return Array.isArray(trail) ? trail : []
  if (name === 'home') return [homeVisit()]
  if (route.meta?.hideBreadcrumb || name === 'notFound' || name === 'oauthCallback') return []

  const key = breadcrumbRouteKey(route)
  if (!key) return Array.isArray(trail) ? trail : []

  const page = {
    key,
    name,
    label: String(currentItem?.label || route.meta?.breadcrumbLabel || route.meta?.title || '当前页面'),
    to: route.fullPath ? { path: route.fullPath } : { name, params: route.params, query: route.query },
    isHome: false,
  }
  const existing = Array.isArray(trail) && trail.length ? trail : [homeVisit()]
  const previousIndex = existing.findIndex((item) => item.key === key)
  if (previousIndex >= 0) {
    return [...existing.slice(0, previousIndex), page]
  }
  return [...existing, page]
}

/** 离开当前页前保存动态标题，供后续面包屑回看时继续显示。 */
export function updateCurrentBreadcrumbVisit(trail, route, currentItem) {
  if (!Array.isArray(trail) || !trail.length || !currentItem?.label) return trail || []
  const key = breadcrumbRouteKey(route)
  const lastIndex = trail.length - 1
  if (trail[lastIndex]?.key !== key) return trail
  return trail.map((item, index) =>
    index === lastIndex ? { ...item, label: String(currentItem.label) } : item,
  )
}
