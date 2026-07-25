/**
 * 清洗 AI 面向用户展示的正文：去掉内部标识，避免泄露后端字段。
 */
export function sanitizeAiDisplayText(raw) {
  let text = raw == null ? '' : String(raw)
  if (!text) return ''

  // 半角/全角括号与冒号：`(meme_id: 13)` / `（meme_id：13）`
  text = text.replace(/[（(]\s*meme[_\s-]?id\s*[=:：]\s*[^)）]+[)）]/gi, '')
  text = text.replace(/\[\s*meme[_\s-]?id\s*[=:：]\s*[^\]]+\]/gi, '')
  text = text.replace(/\bmeme[_\s-]?id\s*[=:：]\s*[A-Za-z0-9_-]+/gi, '')
  text = text.replace(/\b(request|session)[_\s-]?id\s*[=:：]\s*[A-Za-z0-9_-]+/gi, '')
  text = text.replace(/\bscore\s*[=:：]\s*-?\d+(\.\d+)?/gi, '')
  text = text.replace(/梗\s*#\s*\d+/g, '')
  text = text.replace(/[ \t]{2,}/g, ' ')
  text = text.replace(/[ \t]+\n/g, '\n')
  text = text.replace(/\n{3,}/g, '\n\n')
  return text.trim()
}
