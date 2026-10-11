const assert = require('node:assert/strict')
const Module = require('node:module')
const path = require('node:path')
const test = require('node:test')
const babel = require('@babel/core')

const routerPath = path.resolve(__dirname, '../src/router/index.js')
const breadcrumbPath = path.resolve(__dirname, '../src/utils/pageBreadcrumb.js')

/** 加载真实路由与面包屑解析器，只替换 Vue Router 和 Pinia 的运行边界。 */
function loadModule(filePath, overrides = {}) {
  const originalLoad = Module._load
  Module._load = function load(request, parent, isMain) {
    if (Object.hasOwn(overrides, request)) return overrides[request]
    return originalLoad.call(this, request, parent, isMain)
  }

  try {
    const source = babel.transformFileSync(filePath, {
      plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')],
    }).code
    const loaded = new Module(filePath, module)
    loaded.filename = filePath
    loaded.paths = Module._nodeModulePaths(path.dirname(filePath))
    loaded._compile(source, filePath)
    return loaded.exports
  } finally {
    Module._load = originalLoad
  }
}

test('trend route renders a home breadcrumb followed by its page title', () => {
  let registeredRoutes = []
  const routerStub = {
    createRouter: ({ routes }) => {
      registeredRoutes = routes
      return { beforeEach() {}, afterEach() {} }
    },
    createWebHistory: () => ({}),
  }
  const storeStub = { useAuthStore: () => ({}), useBreadcrumbStore: () => ({}) }
  const pageBreadcrumb = loadModule(breadcrumbPath)
  const { resolveRouteBreadcrumbs } = pageBreadcrumb
  loadModule(routerPath, {
    'vue-router': routerStub,
    '@/stores/auth': { useAuthStore: storeStub.useAuthStore },
    '@/stores/breadcrumb': { useBreadcrumbStore: storeStub.useBreadcrumbStore },
    '@/utils/pageBreadcrumb': pageBreadcrumb,
    '@/utils/breadcrumbTrail.mjs': {
      recordBreadcrumbVisit: (trail) => trail,
      updateCurrentBreadcrumbVisit: (trail) => trail,
    },
  })

  const route = registeredRoutes.find((item) => item.name === 'trends')
  const breadcrumbs = resolveRouteBreadcrumbs({
    name: route.name,
    meta: route.meta || {},
    query: {},
    params: {},
  })

  assert.deepEqual(breadcrumbs.map((item) => item.label), ['主页', '热梗趋势'])
  assert.equal(breadcrumbs.at(-1).current, true)
})

test('breadcrumb follows visited route order and keeps the current dynamic title', () => {
  const { resolveRouteBreadcrumbs } = loadModule(breadcrumbPath)
  const breadcrumbs = resolveRouteBreadcrumbs({
    name: 'memeDetail',
    fullPath: '/detail/42',
    meta: {},
    params: { id: '42' },
    query: {},
  }, {
    patch: { meme: { name: '基尼太美' } },
    trail: [
      { key: 'home', label: '主页', to: { name: 'home' }, isHome: true },
      { key: 'trends', label: '热梗趋势', to: { path: '/trends' } },
      { key: 'memeDetail:42', label: '旧标题', to: { path: '/detail/42' } },
    ],
  })

  assert.deepEqual(breadcrumbs.map((item) => item.label), ['主页', '热梗趋势', '基尼太美'])
  assert.deepEqual(breadcrumbs[1].to, { path: '/trends' })
  assert.equal(breadcrumbs.at(-1).current, true)
})

test('records a breadcrumb visit only after the route succeeds', () => {
  let afterEachHook
  let recordedVisit
  const breadcrumbStore = {
    patch: {},
    trail: [],
    recordVisit: (route, item) => { recordedVisit = { route, item } },
    updateCurrentVisit() {},
    clearPatch() {},
  }
  const routerStub = {
    createRouter: () => ({
      beforeEach() {},
      afterEach: (hook) => { afterEachHook = hook },
    }),
    createWebHistory: () => ({}),
  }
  const pageBreadcrumb = loadModule(breadcrumbPath)
  loadModule(routerPath, {
    'vue-router': routerStub,
    '@/stores/auth': { useAuthStore: () => ({}) },
    '@/stores/breadcrumb': { useBreadcrumbStore: () => breadcrumbStore },
    '@/utils/pageBreadcrumb': pageBreadcrumb,
  })

  const route = {
    name: 'trends',
    fullPath: '/trends',
    meta: { breadcrumbLabel: '热梗趋势' },
    params: {},
    query: {},
  }
  afterEachHook(route, { name: 'home' }, undefined)

  assert.equal(recordedVisit.route, route)
  assert.equal(recordedVisit.item.label, '热梗趋势')
})
