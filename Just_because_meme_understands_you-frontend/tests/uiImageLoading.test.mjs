import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveImageLoadingMode } from '../src/utils/imageLoading.mjs'

test('uses native lazy loading by default for meme images', () => {
  assert.equal(resolveImageLoadingMode(), 'lazy')
  assert.equal(resolveImageLoadingMode(true), 'lazy')
})

test('allows images that need immediate display to opt out', () => {
  assert.equal(resolveImageLoadingMode(false), 'eager')
})
