import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { parse } from '@vue/compiler-sfc'

const componentPath = new URL('../src/components/auth/CaptchaField.vue', import.meta.url)
const componentSource = readFileSync(componentPath, 'utf8')
const { descriptor, errors } = parse(componentSource)
assert.deepEqual(errors, [])

// 在隔离环境中执行真实组件方法，只替代它使用的网络与提示依赖。
const executableScript = descriptor.script.content
  .replace(/^import\s+.*?from\s+['"][^'"]+['"];?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const sandbox = {
  module: { exports: {} }, // 接收转换后的组件对象
  fetchCaptcha: () => Promise.resolve({}), // 组件初始化依赖的验证码请求
  toast: { error: () => {} }, // 组件初始化依赖的提示服务
}
runInNewContext(executableScript, sandbox)
const onCodeInput = sandbox.module.exports.methods.onCodeInput

function emitCaptchaCode(value) {
  let emittedValue = null
  onCodeInput.call({
    $emit: (eventName, nextValue) => {
      if (eventName === 'update:captchaCode') emittedValue = nextValue
    },
  }, value)
  return emittedValue
}

test('preserves lowercase, uppercase, and mixed-case captcha input', () => {
  assert.equal(emitCaptchaCode('n57p'), 'n57p')
  assert.equal(emitCaptchaCode('N57P'), 'N57P')
  assert.equal(emitCaptchaCode('n57P'), 'n57P')
})

test('clearing the captcha input emits an empty string', () => {
  assert.equal(emitCaptchaCode(null), '')
})
