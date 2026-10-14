import test from 'node:test'
import assert from 'node:assert/strict'
import {
  appendUniqueFeedItems,
  hasNextFeedPage,
  shouldReobserveFeedSentinel,
} from '../src/utils/feedPagination.mjs'

test('appends new feed items without duplicating ids', () => {
  const existing = [{ id: 1, name: 'first' }, { id: 2, name: 'second' }]
  const nextPage = [{ id: '2', name: 'duplicate' }, { id: 3, name: 'third' }]

  assert.deepEqual(appendUniqueFeedItems(existing, nextPage), [
    { id: 1, name: 'first' },
    { id: 2, name: 'second' },
    { id: 3, name: 'third' },
  ])
})

test('uses total and current page when explicit hasMore is absent', () => {
  assert.equal(hasNextFeedPage({ total: 25, hasMore: false }, 12, 12, 2), true)
  assert.equal(hasNextFeedPage({ total: 12 }, 12, 12, 1), false)
})

test('honors explicit hasMore for legacy paged responses', () => {
  assert.equal(hasNextFeedPage({ total: 12, hasMore: true }, 12, 12, 1), true)
})

test('falls back to a full batch when legacy responses omit a usable total', () => {
  assert.equal(hasNextFeedPage({ total: 0, hasMore: false }, 12, 12, 1), true)
  assert.equal(hasNextFeedPage({ total: 0, hasMore: false }, 5, 12, 1), false)
})

test('reobserves the feed sentinel after a page settles while more items remain', () => {
  assert.equal(shouldReobserveFeedSentinel({
    feedHasMore: true, // 还有待加载的后续页
    feedLoading: false, // 当前没有进行中的请求
    feedSwitching: false, // 当前没有切换排序或筛选条件
    feedError: '', // 上一次请求没有失败
    itemCount: 24, // 页面中已有可继续浏览的梗图
  }), true)
})

test('does not reobserve the feed sentinel while loading, on error, or after the last page', () => {
  const state = {
    feedHasMore: true, // 还有待加载的后续页
    feedLoading: false, // 当前没有进行中的请求
    feedSwitching: false, // 当前没有切换排序或筛选条件
    feedError: '', // 上一次请求没有失败
    itemCount: 24, // 页面中已有可继续浏览的梗图
  }

  assert.equal(shouldReobserveFeedSentinel({ ...state, feedLoading: true }), false)
  assert.equal(shouldReobserveFeedSentinel({ ...state, feedSwitching: true }), false)
  assert.equal(shouldReobserveFeedSentinel({ ...state, feedError: '网络异常' }), false)
  assert.equal(shouldReobserveFeedSentinel({ ...state, feedHasMore: false }), false)
  assert.equal(shouldReobserveFeedSentinel({ ...state, itemCount: 0 }), false)
  assert.equal(shouldReobserveFeedSentinel({
    ...state,
    observerActive: true, // 已存在有效观察器时不重复创建。
  }), false)
})
