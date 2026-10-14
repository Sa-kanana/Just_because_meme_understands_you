import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { buildMemeEditPayload, getMemeFormBlockReason, mapMemeDetailToEditForm } from '../src/utils/memeEditForm.mjs'

const memeApiSource = readFileSync(new URL('../src/api/meme.js', import.meta.url), 'utf8') // 发布梗使用的接口源码。
const publishFunctionStart = memeApiSource.indexOf('export function publishMeme(') // 发布梗接口定义起始位置。
const nextFunctionStart = memeApiSource.indexOf('\n/**', publishFunctionStart) // 发布接口后的下一个文档注释位置。
const publishFunctionSource = memeApiSource.slice(publishFunctionStart, nextFunctionStart) // 发布接口函数源码片段。

test('maps an existing meme detail into editable fields without mixing media and links', () => {
  const form = mapMemeDetailToEditForm({
    name: '旧名称',
    introduction: '旧介绍',
    image: 'https://cdn.example/cover.png',
    memeTag: [{ id: 2, isPrimary: true }, { id: '5', isPrimary: false }],
    links: [
      { type: 'media', url: 'https://cdn.example/one.gif', sortOrder: 0 },
      { type: 'article', title: '出处', url: 'https://example.com/source', sortOrder: 1 },
    ],
  })

  assert.deepEqual(form, {
    name: '旧名称',
    introduction: '旧介绍',
    image: 'https://cdn.example/cover.png',
    primaryTagId: 2,
    tagIds: ['5'],
    resources: [{ type: 'article', title: '出处', url: 'https://example.com/source', sortOrder: 1 }],
    resourceUrls: ['https://cdn.example/one.gif'],
  })
})

test('builds the edit payload from current fields and normalizes link order', () => {
  const payload = buildMemeEditPayload({
    name: ' 新名称 ',
    introduction: ' 介绍 ',
    image: ' cover ',
    primaryTagId: 3,
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
    primaryTagId: 3,
    tagIds: [4],
    resources: [{ type: 'link', title: '链接', url: 'https://example.com', sortOrder: 0 }],
    resourceUrls: ['media'],
  })
})

test('publish request includes the selected primary tag id', () => {
  assert.match(publishFunctionSource, /primaryTagId:\s*payload\.primaryTagId/)
})

const validPublishForm = {
  name: '测试梗', // 中文注释：梗名称为必填字段。
  introduction: '介绍这个梗的来源。', // 中文注释：梗介绍为必填字段。
  primaryTagId: 1, // 中文注释：主标签是选填字段。
  tagIds: [2, 3, 4], // 中文注释：补充标签是选填字段。
}

test('publish form requires both a name and an introduction', () => {
  assert.equal(getMemeFormBlockReason({ ...validPublishForm, name: '  ' }), '请先填写梗名称')
  assert.equal(getMemeFormBlockReason({ ...validPublishForm, introduction: '  ' }), '请先填写梗介绍')
})

test('publish form allows all optional tags to be empty and validates selected tags', () => {
  assert.equal(getMemeFormBlockReason({ name: '测试梗', introduction: '介绍' }), '')
  assert.equal(getMemeFormBlockReason({ ...validPublishForm, primaryTagId: null, tagIds: [] }), '')
  assert.equal(getMemeFormBlockReason({ ...validPublishForm, tagIds: [2, 3, 4, 5, 6, 7, 8, 9] }), '补充标签最多选择 7 个，请移除 1 个')
  assert.equal(getMemeFormBlockReason({ ...validPublishForm, tagIds: [2, 2, 3] }), '补充标签不能重复')
  assert.equal(getMemeFormBlockReason({ ...validPublishForm, tagIds: [1, 2, 3] }), '主标签不能重复作为补充标签')
})

test('publish form only blocks while an optional cover is uploading', () => {
  assert.equal(getMemeFormBlockReason(validPublishForm, { coverUploading: true }), '封面正在上传，请稍候')
  assert.equal(getMemeFormBlockReason(validPublishForm), '')
})
