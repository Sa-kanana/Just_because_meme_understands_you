const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const componentPath = path.join(
  __dirname,
  '..',
  'src',
  'components',
  'home',
  'HomeHeroBanner.vue',
)
const component = fs.readFileSync(componentPath, 'utf8')
const styles = component.match(/<style scoped>([\s\S]*?)<\/style>/)?.[1] || ''
const cssRules = Array.from(styles.matchAll(/([^{}]+)\{([^{}]*)\}/g), ([, selector, declarations]) => ({
  selector,
  declarations,
}))

test('首页轮播使用紧凑高度并完整显示管理端预览图', () => {
  const mediaRule = cssRules.find(({ selector }) => selector.trim() === '.home-hero__media')
  const shellRule = cssRules.find(({ selector }) => selector.trim() === '.home-hero__shell')

  assert.ok(mediaRule, '应定义首页轮播图片容器样式')
  assert.ok(shellRule, '应定义左右并排的轮播外框')
  assert.match(shellRule.declarations, /height:\s*clamp\(300px,\s*23vw,\s*390px\)\s*;/)
  assert.match(mediaRule.declarations, /height:\s*100%\s*;/)
  assert.match(mediaRule.declarations, /aspect-ratio:\s*auto\s*;/)

  const imageRule = cssRules.find(({ selector }) => selector.trim() === '.home-hero__slide-btn :deep(.ui-image img)')
  assert.ok(imageRule, '应定义轮播图片的实际显示规则')
  assert.match(imageRule.declarations, /object-fit:\s*contain\s*;/)
  assert.match(component, /class="home-hero__backdrop"[\s\S]*?fit="cover"/)
  assert.match(component, /:src="item\.img_url"[\s\S]*?fit="contain"/)
})
