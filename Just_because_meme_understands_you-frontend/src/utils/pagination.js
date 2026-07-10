/**
 * 解析分页接口是否还有下一页。
 * 优先使用后端 hasMore；否则根据本页条数与已加载总数推断。
 *
 * @param {Object|null|undefined} data
 * @param {number} batchSize - 本次响应条数
 * @param {number} pageSize - 分页大小
 * @param {number} loadedCount - 已累计加载条数
 */
export function resolvePageHasMore(data, batchSize, pageSize, loadedCount) {
  if (data && data.hasMore != null) {
    return !!data.hasMore
  }
  const size = Number(pageSize) || 0
  const batch = Number(batchSize) || 0
  const loaded = Number(loadedCount) || 0
  if (size > 0 && batch < size) {
    return false
  }
  const total = Number(data?.total)
  if (Number.isFinite(total) && total >= 0) {
    return loaded < total
  }
  return size > 0 && batch >= size
}
