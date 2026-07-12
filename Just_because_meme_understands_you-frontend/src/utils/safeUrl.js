/**
 * 外部链接协议白名单，防止 javascript: / data: 等伪协议 XSS。
 */

export function isSafeExternalUrl(url) {
  if (url == null || typeof url !== 'string') return false
  const trimmed = url.trim()
  if (!trimmed) return false
  try {
    const parsed = new URL(trimmed)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

/** 不安全则返回空字符串，供模板绑定使用 */
export function sanitizeExternalUrl(url) {
  return isSafeExternalUrl(url) ? String(url).trim() : ''
}
