import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import postcss from 'postcss'

const componentPath = new URL('../src/views/UserProfile.vue', import.meta.url) // 被测个人主页组件
const componentSource = readFileSync(componentPath, 'utf8') // 个人主页 Vue 源码
const { descriptor, errors } = parse(componentSource) // 解析模板与样式块
assert.equal(errors.length, 0)

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
