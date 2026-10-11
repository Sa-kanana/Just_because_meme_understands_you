import assert from 'node:assert/strict'
import test from 'node:test'
import { runBatchOperations } from '../src/utils/batchOperations.mjs'

test('continues after an item fails and returns item-level outcomes', async () => {
  const processed = []
  const items = [{ id: 'a' }, { id: 'b' }, { id: 'c' }]

  const result = await runBatchOperations(items, async (item) => {
    processed.push(item.id)
    if (item.id === 'b') throw new Error('网络错误')
  })

  assert.deepEqual(processed, ['a', 'b', 'c'])
  assert.deepEqual(result.succeeded.map((item) => item.id), ['a', 'c'])
  assert.equal(result.failed.length, 1)
  assert.equal(result.failed[0].item.id, 'b')
  assert.equal(result.failed[0].error.message, '网络错误')
})

test('returns an empty result without invoking an operation for no selection', async () => {
  let invoked = false
  const result = await runBatchOperations([], async () => { invoked = true })

  assert.equal(invoked, false)
  assert.deepEqual(result, { succeeded: [], failed: [] })
})

test('limits concurrent requests to four records', async () => {
  let active = 0
  let maxActive = 0
  const items = Array.from({ length: 8 }, (_, id) => ({ id }))

  const result = await runBatchOperations(items, async () => {
    active += 1
    maxActive = Math.max(maxActive, active)
    await new Promise((resolve) => setTimeout(resolve, 5))
    active -= 1
  })

  assert.equal(maxActive, 4)
  assert.equal(result.succeeded.length, items.length)
})
