/**
 * Append a page while keeping the first occurrence of each meme id.
 * @param {Array<Object>} current
 * @param {Array<Object>} nextPage
 * @returns {Array<Object>}
 */
export function appendUniqueFeedItems(current = [], nextPage = []) {
  const result = Array.isArray(current) ? [...current] : []
  const seenIds = new Set(
    result
      .filter((item) => item && item.id != null)
      .map((item) => String(item.id)),
  )

  for (const item of Array.isArray(nextPage) ? nextPage : []) {
    if (!item || item.id == null) {
      result.push(item)
      continue
    }
    const id = String(item.id)
    if (seenIds.has(id)) continue
    seenIds.add(id)
    result.push(item)
  }

  return result
}

/**
 * Decide whether a later page can exist using the response total when available.
 * Legacy responses may omit a useful total, so a full batch remains a safe fallback.
 * @param {Object|null|undefined} data
 * @param {number} batchSize
 * @param {number} pageSize
 * @param {number} page
 * @returns {boolean}
 */
export function hasNextFeedPage(data, batchSize, pageSize, page) {
  const size = Number(pageSize) || 0
  const currentPage = Number(page) || 0
  const total = Number(data?.total)

  if (data?.hasMore === true) return true
  if (Number.isFinite(total) && total > 0 && size > 0 && currentPage > 0) {
    return currentPage * size < total
  }
  return size > 0 && Number(batchSize) >= size
}

/**
 * 判断当前列表更新完成后是否需要重新观察懒加载哨兵。
 * @param {Object} state 当前梗图列表状态
 * @param {boolean} state.feedHasMore 是否还有下一页
 * @param {boolean} state.feedLoading 是否正在加载
 * @param {boolean} state.feedSwitching 是否正在切换列表
 * @param {string} state.feedError 当前加载错误信息
 * @param {number} state.itemCount 当前已展示的梗图数量
 * @returns {boolean} 当前状态是否允许继续懒加载
 */
export function shouldReobserveFeedSentinel({
  feedHasMore,
  feedLoading,
  feedSwitching,
  feedError,
  itemCount,
} = {}) {
  return Boolean(
    feedHasMore
    && !feedLoading
    && !feedSwitching
    && !feedError
    && Number(itemCount) > 0,
  )
}
