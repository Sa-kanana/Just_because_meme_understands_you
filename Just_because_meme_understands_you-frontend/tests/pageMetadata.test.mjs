import test from 'node:test'
import assert from 'node:assert/strict'
import { clampPageToTotal, hasPageTotal, normalizePageMetadata } from '../src/utils/pageMetadata.mjs'

test('preserves complete backend page metadata', () => {
  assert.deepEqual(
    normalizePageMetadata({ page: 2, size: 12, total: 25, hasMore: true }),
    { page: 2, size: 12, total: 25, hasMore: true },
  )
})

test('uses supplied defaults for missing or invalid metadata', () => {
  assert.deepEqual(
    normalizePageMetadata({ page: 0.2, size: 0.5, total: -1 }, { page: 3, size: 12, total: 30, hasMore: true }),
    { page: 3, size: 12, total: 30, hasMore: true },
  )
})

test('uses safe defaults when the response has no page metadata', () => {
  assert.deepEqual(
    normalizePageMetadata(null, { page: 2, size: 12 }),
    { page: 2, size: 12, total: 0, hasMore: false },
  )
})

test('clamps an expired page to the last page available after the total shrinks', () => {
  assert.equal(clampPageToTotal(3, 20, 12), 2)
  assert.equal(clampPageToTotal(2, 0, 12), 1)
  assert.equal(clampPageToTotal(2, 25, 12), 2)
})

test('requests a list fallback only when bootstrap total is missing or invalid', () => {
  assert.equal(hasPageTotal({ page: 1, total: 0 }), true)
  assert.equal(hasPageTotal({ page: 1, total: '25' }), true)
  assert.equal(hasPageTotal({ page: 1, total: -1 }), false)
  assert.equal(hasPageTotal({ page: 1 }), false)
})
