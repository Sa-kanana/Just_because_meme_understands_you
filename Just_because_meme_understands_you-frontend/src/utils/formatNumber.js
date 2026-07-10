/**
 * 格式化展示数字（浏览/点赞/评论等）
 */
export function formatCompactNumber(num) {
  if (num == null) return '0'
  const n = Number(num)
  if (!Number.isFinite(n)) return '0'
  if (n >= 1e8) return `${(n / 1e8).toFixed(1).replace(/\.0$/, '')}亿`
  if (n >= 1e4) return `${(n / 1e4).toFixed(1).replace(/\.0$/, '')}万`
  return String(Math.max(0, Math.trunc(n)))
}
