import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import postcss from 'postcss'

const componentPath = new URL('../src/views/UserProfile.vue', import.meta.url) // 被测个人主页组件
const componentSource = readFileSync(componentPath, 'utf8') // 个人主页 Vue 源码
const userApiSource = readFileSync(new URL('../src/api/user.js', import.meta.url), 'utf8') // 用户主页接口源码
const { descriptor, errors } = parse(componentSource) // 解析模板与样式块
assert.equal(errors.length, 0)
const purgePublishedMemeNameSource = componentSource.match(/purgePublishedMemeName\(\)\s*\{([\s\S]*?)\n {4}\},/) // 提取删除确认展示名称
assert.ok(purgePublishedMemeNameSource, '个人主页应规范化彻底删除确认名称')
const purgePublishedMemeName = new Function(
  `return function purgePublishedMemeName() { ${purgePublishedMemeNameSource[1]} }`,
)() // 执行真实的确认名称规范化逻辑
const purgeConfirmMatchedSource = componentSource.match(/purgeConfirmMatched\(\)\s*\{([\s\S]*?)\n {4}\},/) // 提取彻底删除确认条件
assert.ok(purgeConfirmMatchedSource, '个人主页应提供彻底删除确认条件')
const purgeConfirmMatched = new Function(
  `return function purgeConfirmMatched() { ${purgeConfirmMatchedSource[1]} }`,
)() // 执行真实的确认条件实现
const confirmPurgePublishedSource = componentSource.match(/async confirmPurgePublished\(\)\s*\{([\s\S]*?)\n {4}\},/) // 提取彻底删除提交逻辑
assert.ok(confirmPurgePublishedSource, '个人主页应提供彻底删除提交处理')
const confirmPurgePublished = new Function(
  `return async function confirmPurgePublished() { ${confirmPurgePublishedSource[1]} }`,
)() // 执行真实的彻底删除提交逻辑

const scopedStyle = descriptor.styles.find((style) => style.scoped) // 个人主页局部样式
const stylesheet = postcss.parse(scopedStyle.content) // 解析 CSS 规则

function findRule(container, selector) {
  return container.nodes.find((node) => node.type === 'rule' && node.selector === selector)
}

function findDeclaration(rule, property) {
  const declaration = rule.nodes.find(
    (node) => node.type === 'decl' && node.prop === property,
  )
  return declaration && declaration.value
}

test('keeps profile meme cards left-aligned in a responsive grid', () => {
  const gridRule = findRule(stylesheet, '.meme-grid') // 桌面端梗图网格

  assert.equal(findDeclaration(gridRule, 'display'), 'grid')
  assert.equal(findDeclaration(gridRule, 'grid-template-columns'), 'repeat(4, minmax(0, 1fr))')
  assert.equal(findDeclaration(gridRule, 'gap'), '14px')
  assert.notEqual(findDeclaration(gridRule, 'justify-content'), 'center')

  const wideTabletRule = stylesheet.nodes
    .find((node) => node.type === 'atrule' && node.params === '(max-width: 1440px)')
  const wideTabletGridRule = wideTabletRule && findRule(wideTabletRule, '.meme-grid')
  assert.equal(
    findDeclaration(wideTabletGridRule, 'grid-template-columns'),
    'repeat(3, minmax(0, 1fr))',
  )

  const tabletRule = stylesheet.nodes
    .find((node) => node.type === 'atrule' && node.params === '(max-width: 1180px)')
  const tabletGridRule = tabletRule && findRule(tabletRule, '.meme-grid') // 平板端两列网格
  assert.equal(
    findDeclaration(tabletGridRule, 'grid-template-columns'),
    'repeat(2, minmax(0, 1fr))',
  )

  const mobileRule = stylesheet.nodes
    .find((node) => node.type === 'atrule' && node.params === '(max-width: 680px)')
  const mobileGridRule = mobileRule && findRule(mobileRule, '.meme-grid') // 手机端单列网格
  assert.equal(findDeclaration(mobileGridRule, 'grid-template-columns'), '1fr')
})

test('replaces the published-list hint with accessible work search controls', () => {
  const template = descriptor.template.content // 个人主页发布作品模板

  assert.doesNotMatch(template, /点击卡片看详情/)
  assert.match(template, /type="search"/)
  assert.match(template, /v-model="publishedSearchInput"/)
  assert.match(template, /@keyup\.enter\.prevent="applyPublishedSearch"/)
  assert.match(template, /@search="!publishedSearchInput && clearPublishedSearch\(\)"/)
  assert.match(template, /@click="clearPublishedSearch"/)
  assert.match(template, /aria-label="搜索作品名称"/)
  assert.match(componentSource, /keyword: this\.publishedSearchKeyword/)
  assert.match(userApiSource, /query\.set\('keyword', searchKeyword\)/)
  assert.match(componentSource, /publishedDisplayTotal\(\) \{[\s\S]*?if \(this\.publishedSearchKeyword\) return this\.publishedPageTotal/)
  assert.match(componentSource, /applyPublishedSearch\([\s\S]*publishedPageNo = 1/)
})

test('does not submit or start loading before a blank meme name is confirmed', async () => {
  const target = { name: '   ' } // 模拟历史数据中的纯空白梗名称
  const expectedName = purgePublishedMemeName.call({ purgePublishedTarget: target })

  assert.equal(expectedName, '未命名梗图')
  const context = {
    purgePublishedTarget: target, // 当前待删除梗
    purgeConfirmName: '', // 用户尚未输入确认名称
    purgePublishedMemeName: expectedName, // 页面显示的规范化确认名称
  }

  context.purgeConfirmMatched = purgeConfirmMatched.call(context)
  assert.equal(context.purgeConfirmMatched, false)
  context.purgePublishedSubmitting = false // 当前删除请求的加载状态
  await confirmPurgePublished.call(context)
  assert.equal(context.purgePublishedSubmitting, false)

  assert.equal(purgeConfirmMatched.call({
    purgePublishedTarget: target, // 当前待删除梗
    purgeConfirmName: ' 未命名梗图 ', // 用户输入页面展示的确认名称
    purgePublishedMemeName: expectedName, // 页面显示的规范化确认名称
  }), true)
})

test('shows an unconfirmed purge action as disabled instead of loading', () => {
  const disabledButtonRule = findRule(stylesheet, '.profile-dlg-btn:disabled')
  const disabledDangerRule = findRule(
    stylesheet,
    '.profile-dlg-btn--danger:disabled:not(.profile-dlg-btn--loading)',
  )

  assert.equal(findDeclaration(disabledButtonRule, 'cursor'), 'not-allowed')
  assert.equal(findDeclaration(disabledDangerRule, 'background'), 'var(--meme-bg-muted)')
  assert.equal(findDeclaration(disabledDangerRule, 'box-shadow'), 'none')
  assert.match(descriptor.template.content, /请先输入梗名/)
  assert.match(descriptor.template.content, /profile-dlg-btn--loading/)
})
