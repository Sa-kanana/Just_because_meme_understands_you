import test from 'node:test'
import assert from 'node:assert/strict'
import { readPublishDraft, savePublishDraft, clearPublishDraft } from '../src/utils/publishDraft.mjs'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
}

test('saving and restoring a draft keeps only supported form fields', () => {
  const storage = memoryStorage()
  const file = { name: 'private.png', size: 1234 }
  const form = {
    name: '测试梗', introduction: '出处', image: 'https://example.com/cover.png',
    tagIds: [2, 3], resources: [{ type: 'video', title: '原视频', url: 'https://example.com', sortOrder: 0, file }],
    resourceUrls: ['https://example.com/extra.png'], file,
  }
  assert.equal(savePublishDraft(storage, { id: 42 }, form), true)
  assert.deepEqual(readPublishDraft(storage, { userId: 42 }), {
    name: '测试梗', introduction: '出处', image: 'https://example.com/cover.png',
    tagIds: [2, 3], resources: [{ type: 'video', title: '原视频', url: 'https://example.com', sortOrder: 0 }],
    resourceUrls: ['https://example.com/extra.png'],
  })
  assert.equal(JSON.stringify(readPublishDraft(storage, { id: 42 })).includes('private.png'), false)
})

test('a draft belongs only to its logged-in user', () => {
  const storage = memoryStorage()
  savePublishDraft(storage, { id: 42 }, { name: '甲的梗' })
  assert.equal(readPublishDraft(storage, { id: 43 }), null)
  assert.equal(readPublishDraft(storage, null), null)
  assert.equal(savePublishDraft(storage, null, { name: '游客梗' }), false)
  assert.equal(readPublishDraft(storage, { id: 42 }).name, '甲的梗')
})

test('clearing removes a draft and an empty form does not leave a saved draft', () => {
  const storage = memoryStorage()
  savePublishDraft(storage, { id: 42 }, { name: '未完成' })
  clearPublishDraft(storage, { id: 42 })
  assert.equal(readPublishDraft(storage, { id: 42 }), null)
  savePublishDraft(storage, { id: 42 }, { name: '未完成' })
  savePublishDraft(storage, { id: 42 }, { name: '', introduction: '', image: '', tagIds: [], resources: [], resourceUrls: [] })
  assert.equal(readPublishDraft(storage, { id: 42 }), null)
})

test('corrupt or obsolete stored drafts are ignored safely', () => {
  const storage = memoryStorage()
  storage.setItem('meme_publish_draft_v1:42', '{bad json')
  assert.equal(readPublishDraft(storage, { id: 42 }), null)
  storage.setItem('meme_publish_draft_v1:42', JSON.stringify({ version: 99, form: { name: '旧版' } }))
  assert.equal(readPublishDraft(storage, { id: 42 }), null)
})
