import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeUserId,
  buildSafetyState,
  isSuppressedUser,
  filterSuppressedItems,
} from '../src/utils/userSafety.mjs'

test('normalizes positive numeric ids without losing large id precision', () => {
  assert.equal(normalizeUserId('00042'), '42')
  assert.equal(normalizeUserId(12), '12')
  assert.equal(normalizeUserId('9007199254740993123'), '9007199254740993123')
  assert.equal(normalizeUserId('0'), '')
  assert.equal(normalizeUserId('8x'), '')
  assert.equal(normalizeUserId(null), '')
})

test('builds mute and bilateral block sets only for an authenticated viewer', () => {
  const rows = [
    { targetUserId: 8, type: 'MUTE', direction: 'OUTGOING' },
    { targetUserId: 9, type: 'BLOCK', direction: 'OUTGOING' },
    { targetUserId: 10, type: 'BLOCK', direction: 'INCOMING' },
  ]
  const state = buildSafetyState(rows, 7, true)
  assert.deepEqual([...state.mutedUserIds], ['8'])
  assert.deepEqual([...state.blockedUserIds], ['9', '10'])
  assert.equal(isSuppressedUser(state, 8), true)
  assert.equal(isSuppressedUser(state, 10), true)
  assert.equal(isSuppressedUser(state, 7), false)
  assert.equal(isSuppressedUser(buildSafetyState(rows, 7, false), 8), false)
})

test('filters suppressed authors while preserving anonymous browsing and other order', () => {
  const items = [{ userId: 8 }, { userId: 3 }, { userId: 9 }]
  const state = buildSafetyState([
    { targetUserId: 8, type: 'MUTE' },
    { targetUserId: 9, type: 'BLOCK' },
  ], 7, true)
  assert.deepEqual(filterSuppressedItems(items, state), [{ userId: 3 }])
  assert.equal(filterSuppressedItems(items, buildSafetyState([], null, false)).length, 3)
})
