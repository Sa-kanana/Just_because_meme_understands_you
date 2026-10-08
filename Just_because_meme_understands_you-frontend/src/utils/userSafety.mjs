/** Normalize MySQL bigint identifiers without converting through JS Number. */
export function normalizeUserId(value) {
  if (value == null) return ''
  const raw = String(value).trim()
  if (!/^\d+$/.test(raw)) return ''
  const normalized = raw.replace(/^0+(?=\d)/, '')
  return normalized === '0' ? '' : normalized
}

export function canApplySafetyStatus({
  requestTargetId,
  requestViewerId,
  currentTargetId,
  currentViewerId,
  requestEpoch,
  currentEpoch,
}) {
  return String(requestTargetId || '') === String(currentTargetId || '')
    && String(requestViewerId || '') === String(currentViewerId || '')
    && requestEpoch === currentEpoch
}

export function buildSafetyState(rows, viewerId, authenticated = true) {
  const viewer = normalizeUserId(viewerId)
  const mutedUserIds = new Set()
  const blockedUserIds = new Set()
  if (authenticated && viewer && Array.isArray(rows)) {
    for (const row of rows) {
      const targetId = normalizeUserId(row?.targetUserId)
      if (!targetId || targetId === viewer) continue
      if (row.type === 'MUTE' && row.direction !== 'INCOMING') mutedUserIds.add(targetId)
      if (row.type === 'BLOCK') blockedUserIds.add(targetId)
    }
  }
  return { viewerId: authenticated ? viewer : '', authenticated: !!(authenticated && viewer), mutedUserIds, blockedUserIds }
}

export function isSuppressedUser(state, userId) {
  const id = normalizeUserId(userId)
  if (!state?.authenticated || !id || id === state.viewerId) return false
  return state.mutedUserIds?.has(id) || state.blockedUserIds?.has(id) || false
}

export function filterSuppressedItems(items, state, userIdKey = 'userId') {
  if (!Array.isArray(items)) return []
  if (!state?.authenticated) return items
  return items.filter((item) => !isSuppressedUser(state, item?.[userIdKey]))
}
