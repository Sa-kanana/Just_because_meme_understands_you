/**
 * Normalize paging values received from APIs and use request values as safe fallbacks.
 * @param {unknown} source
 * @param {{ page?: number, size?: number, total?: number, hasMore?: boolean }} [fallback]
 * @returns {{ page: number, size: number, total: number, hasMore: boolean }}
 */
export function normalizePageMetadata(source, fallback = {}) {
  const data = source && typeof source === 'object' && !Array.isArray(source) ? source : {}
  const toPositiveInteger = (value, defaultValue) => {
    const number = Number(value)
    const integer = Math.floor(number)
    return Number.isFinite(number) && integer > 0 ? integer : defaultValue
  }
  const toNonNegativeInteger = (value, defaultValue) => {
    const number = Number(value)
    return Number.isFinite(number) && number >= 0 ? Math.floor(number) : defaultValue
  }
  const fallbackPage = toPositiveInteger(fallback.page, 1)
  const fallbackSize = toPositiveInteger(fallback.size, 10)
  const fallbackTotal = toNonNegativeInteger(fallback.total, 0)

  return {
    page: toPositiveInteger(data.page, fallbackPage),
    size: toPositiveInteger(data.size, fallbackSize),
    total: toNonNegativeInteger(data.total, fallbackTotal),
    hasMore: typeof data.hasMore === 'boolean' ? data.hasMore : Boolean(fallback.hasMore),
  }
}

/**
 * Clamp a requested page to the range represented by the latest total.
 * @param {number|string} page
 * @param {number|string} total
 * @param {number|string} size
 * @returns {number}
 */
export function clampPageToTotal(page, total, size) {
  const metadata = normalizePageMetadata({ page, total, size })
  const lastPage = Math.max(1, Math.ceil(metadata.total / metadata.size))
  return Math.min(metadata.page, lastPage)
}

/**
 * Check whether a response contains a usable total before normalization adds its fallback.
 * @param {unknown} source
 * @returns {boolean}
 */
export function hasPageTotal(source) {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return false
  if (source.total == null || source.total === '') return false
  const total = Number(source.total)
  return Number.isFinite(total) && total >= 0
}
