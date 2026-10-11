/** 对选中的记录逐项执行操作，并保留每项结果，便于展示部分成功。 */
// 限制同时请求数，避免一次批量选择过多记录时集中冲击后端接口。
const MAX_CONCURRENT_BATCH_OPERATIONS = 4

export async function runBatchOperations(items, operation) {
  if (!Array.isArray(items) || items.length === 0) {
    return { succeeded: [], failed: [] }
  }

  const outcomes = new Array(items.length)
  let nextIndex = 0

  async function runNext() {
    while (nextIndex < items.length) {
      const index = nextIndex
      nextIndex += 1
      const item = items[index]

      try {
        await operation(item)
        outcomes[index] = { item, error: null }
      } catch (error) {
        outcomes[index] = {
          item,
          error: error instanceof Error ? error : new Error(String(error || '操作失败')),
        }
      }
    }
  }

  const workerCount = Math.min(MAX_CONCURRENT_BATCH_OPERATIONS, items.length)
  await Promise.all(Array.from({ length: workerCount }, () => runNext()))

  return {
    succeeded: outcomes.filter((outcome) => !outcome.error).map((outcome) => outcome.item),
    failed: outcomes.filter((outcome) => outcome.error).map(({ item, error }) => ({ item, error })),
  }
}
