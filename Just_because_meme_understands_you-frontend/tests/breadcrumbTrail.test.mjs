import test from 'node:test'
import assert from 'node:assert/strict'
import {
  breadcrumbRouteKey,
  recordBreadcrumbVisit,
  updateCurrentBreadcrumbVisit,
} from '../src/utils/breadcrumbTrail.mjs'

test('records visited pages in click order and truncates after revisiting an earlier page', () => {
  const home = { name: 'home', path: '/', fullPath: '/', params: {}, query: {} }
  const trends = {
    name: 'trends', path: '/trends', fullPath: '/trends', params: {}, query: {},
    meta: { breadcrumbLabel: '热梗趋势' },
  }
  const detail = {
    name: 'memeDetail', path: '/detail/42', fullPath: '/detail/42',
    params: { id: '42' }, query: {}, meta: {},
  }

  const visited = recordBreadcrumbVisit(
    recordBreadcrumbVisit(recordBreadcrumbVisit([], home), trends, { label: '热梗趋势' }),
    detail,
    { label: '基尼太美' },
  )
  const returnedToTrends = recordBreadcrumbVisit(visited, trends, { label: '热梗趋势' })

  assert.deepEqual(visited.map((item) => item.label), ['主页', '热梗趋势', '基尼太美'])
  assert.deepEqual(returnedToTrends.map((item) => item.label), ['主页', '热梗趋势'])
})

test('ignores navigation origin fields but distinguishes different search terms', () => {
  const firstSearch = {
    name: 'search', params: {}, query: { keyword: '奶蛙', from: 'home', memeId: '1' },
  }
  const sameSearchFromTrend = {
    name: 'search', params: {}, query: { keyword: '奶蛙', from: 'trends', memeId: '2' },
  }
  const anotherSearch = { name: 'search', params: {}, query: { keyword: '基尼太美' } }

  assert.equal(breadcrumbRouteKey(firstSearch), breadcrumbRouteKey(sameSearchFromTrend))
  assert.notEqual(breadcrumbRouteKey(firstSearch), breadcrumbRouteKey(anotherSearch))
})

test('keeps a loaded dynamic title when the user later visits an earlier breadcrumb', () => {
  const detail = {
    name: 'memeDetail', params: { id: '42' }, query: {},
  }
  const trail = [
    { key: 'home|[]|[]', name: 'home', label: '主页' },
    { key: breadcrumbRouteKey(detail), name: 'memeDetail', label: '梗详情' },
  ]

  const updated = updateCurrentBreadcrumbVisit(trail, detail, { label: '基尼太美' })

  assert.equal(updated[1].label, '基尼太美')
  assert.equal(trail[1].label, '梗详情')
})
