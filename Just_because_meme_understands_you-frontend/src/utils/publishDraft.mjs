const KEY_PREFIX = 'meme_publish_draft_v1:'
const VERSION = 2 // 中文注释：草稿格式版本，当前版本新增了单独的主标签字段。

function draftKey(user) {
  const id = user?.id ?? user?.userId
  if (typeof id !== 'string' && typeof id !== 'number') return null
  const value = String(id).trim()
  return value ? `${KEY_PREFIX}${encodeURIComponent(value)}` : null
}

function stringValue(value) {
  return typeof value === 'string' ? value : ''
}

function normalizeForm(form) {
  if (!form || typeof form !== 'object' || Array.isArray(form)) return null
  return {
    name: stringValue(form.name),
    introduction: stringValue(form.introduction),
    image: stringValue(form.image),
    primaryTagId: typeof form.primaryTagId === 'number' || typeof form.primaryTagId === 'string'
      ? form.primaryTagId
      : null, // 中文注释：主分类标签 ID。
    tagIds: Array.isArray(form.tagIds)
      ? form.tagIds.filter((id) => typeof id === 'string' || typeof id === 'number')
      : [],
    resources: Array.isArray(form.resources)
      ? form.resources.slice(0, 10).filter((item) => item && typeof item === 'object').map((item, index) => ({
        type: stringValue(item.type) || 'link',
        title: stringValue(item.title),
        url: stringValue(item.url),
        sortOrder: index,
      }))
      : [],
    resourceUrls: Array.isArray(form.resourceUrls)
      ? form.resourceUrls.slice(0, 6).filter((url) => typeof url === 'string')
      : [],
  }
}

function hasContent(form) {
  return Boolean(
    form.name.trim() || form.introduction.trim() || form.image || form.primaryTagId ||
    form.tagIds.length || form.resources.length || form.resourceUrls.length
  )
}

export function readPublishDraft(storage, user) {
  const key = draftKey(user)
  if (!key) return null
  try {
    const raw = storage.getItem(key)
    if (!raw) return null
    const stored = JSON.parse(raw)
    if (stored?.version !== VERSION && stored?.version !== 1) return null
    const form = normalizeForm(stored.form)
    if (stored?.version === 1 && form.tagIds.length) {
      form.primaryTagId = form.tagIds[0]
      form.tagIds = form.tagIds.slice(1)
    }
    return form && hasContent(form) ? form : null
  } catch (_) {
    return null
  }
}

export function savePublishDraft(storage, user, form) {
  const key = draftKey(user)
  const normalized = normalizeForm(form)
  if (!key || !normalized) return false
  try {
    if (hasContent(normalized)) {
      storage.setItem(key, JSON.stringify({ version: VERSION, form: normalized }))
    } else {
      storage.removeItem(key)
    }
    return true
  } catch (_) {
    return false
  }
}

export function clearPublishDraft(storage, user) {
  const key = draftKey(user)
  if (!key) return false
  try {
    storage.removeItem(key)
    return true
  } catch (_) {
    return false
  }
}
