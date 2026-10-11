const KEY_PREFIX = 'meme_publish_draft_v1:'
const VERSION = 1

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
    form.name.trim() || form.introduction.trim() || form.image ||
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
    if (stored?.version !== VERSION) return null
    const form = normalizeForm(stored.form)
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
