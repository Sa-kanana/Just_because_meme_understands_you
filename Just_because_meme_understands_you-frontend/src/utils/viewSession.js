const STORAGE_KEY = 'meme_view_session_id'

function generateUuidV4() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const rand = Math.floor(Math.random() * 16)
    const value = char === 'x' ? rand : ((rand & 0x3) | 0x8)
    return value.toString(16)
  })
}

/**
 * 未登录访客的浏览会话 ID，持久化在 localStorage，供 X-View-Session-Id 使用。
 */
export function getViewSessionId() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return generateUuidV4()
  }
  try {
    const existing = window.localStorage.getItem(STORAGE_KEY)
    if (existing && String(existing).trim()) {
      return String(existing).trim()
    }
    const next = generateUuidV4()
    window.localStorage.setItem(STORAGE_KEY, next)
    return next
  } catch {
    return generateUuidV4()
  }
}
