import test from 'node:test'
import assert from 'node:assert/strict'
import { buildMemeEditPayload, mapMemeDetailToEditForm } from '../src/utils/memeEditForm.mjs'

test('maps an existing meme detail into editable fields without mixing media and links', () => {
  const form = mapMemeDetailToEditForm({
    name: '旧名称',
    introduction: '旧介绍',
    image: 'https://cdn.example/cover.png',
    memeTag: [{ id: 2 }, { id: '5' }],
    links: [
      { type: 'media', url: 'https://cdn.example/one.gif', sortOrder: 0 },
      { type: 'article', title: '出处', url: 'https://example.com/source', sortOrder: 1 },
    ],
  })

  assert.deepEqual(form, {
    name: '旧名称',
    introduction: '旧介绍',
    image: 'https://cdn.example/cover.png',
    tagIds: [2, '5'],
    resources: [{ type: 'article', title: '出处', url: 'https://example.com/source', sortOrder: 1 }],
    resourceUrls: ['https://cdn.example/one.gif'],
  })
})

test('builds the edit payload from current fields and normalizes link order', () => {
  const payload = buildMemeEditPayload({
    name: ' 新名称 ',
    introduction: ' 介绍 ',
    image: ' cover ',
    tagIds: [4],
    resources: [
      { type: 'link', title: ' 链接 ', url: ' https://example.com ' },
      { type: 'article', title: '', url: '' },
    ],
    resourceUrls: [' media '],
  })

  assert.deepEqual(payload, {
    name: '新名称',
    introduction: '介绍',
    image: 'cover',
    tagIds: [4],
    resources: [{ type: 'link', title: '链接', url: 'https://example.com', sortOrder: 0 }],
    resourceUrls: ['media'],
  })
})
