/**
 * 短日期：MM-DD（类似 B 站卡片）
 */
export function formatDateShort(str) {
  if (!str) return ''
  const match = String(str).trim().match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (match) return `${match[2]}-${match[3]}`
  return ''
}
