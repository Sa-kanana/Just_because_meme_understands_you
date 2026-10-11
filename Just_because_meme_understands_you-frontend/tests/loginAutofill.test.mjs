import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { parse } from '@vue/compiler-sfc'

const componentPath = new URL('../src/views/Login.vue', import.meta.url)
const componentSource = readFileSync(componentPath, 'utf8')
const { descriptor, errors } = parse(componentSource)
assert.equal(errors.length, 0)
assert.doesNotMatch(
  descriptor.template.content,
  /placeholder="(?:name@example\.com|请输入密码)"/,
  '邮箱和密码已有字段标签，不应再渲染会与浏览器自动填充冲突的占位提示',
)

// 执行登录页真实脚本；组件依赖只在方法被调用时使用。
const executableScript = descriptor.script.content
  .replace(/^import\s+.*?from\s+['"][^'"]+['"];?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const sandbox = {
  module: { exports: {} }, // 接收转换后的登录页组件对象
  AuthPageLayout: {}, // 提供模板注册所需的布局组件
  CaptchaField: {}, // 提供模板注册所需的验证码组件
}
runInNewContext(executableScript, sandbox)
const LoginPage = sandbox.module.exports

function createLoginContext({
  email = '', // 登录表单当前邮箱
  password = '', // 登录表单当前密码
  queryEmail = '', // 路由提供的预填邮箱
  autofilledEmail = 'saved@example.com', // 浏览器保存的邮箱值
  autofilledPassword = 'saved-password', // 浏览器保存的密码值
} = {}) {
  const context = {
    form: {
      email, // 登录表单邮箱
      password, // 登录表单密码
      loginType: 'email', // 登录方式
      captchaId: '', // 当前验证码会话标识
      captchaCode: '', // 当前验证码输入
    },
    $route: { query: { email: queryEmail } }, // 当前登录路由参数
    $refs: {
      emailInput: { inputRef: { value: autofilledEmail } }, // 浏览器填入的邮箱
      passwordInput: { inputRef: { value: autofilledPassword } }, // 浏览器填入的密码
      loginFormRef: { validate: (callback) => callback(false) }, // 阻止测试触发登录请求
    },
    $nextTick: (callback) => callback(), // 在测试中同步执行下一次渲染回调
  }

  return Object.assign(context, LoginPage.methods)
}

test('syncs browser-autofilled credentials into the form when login opens', () => {
  const context = createLoginContext()

  LoginPage.mounted.call(context)

  assert.equal(context.form.email, 'saved@example.com')
  assert.equal(context.form.password, 'saved-password')
})

test('syncs credentials again before form validation in case autofill arrives late', () => {
  const context = createLoginContext({
    autofilledEmail: '', // 模拟自动填充稍后才发生
    autofilledPassword: '', // 模拟自动填充稍后才发生
  })
  context.$refs.emailInput.inputRef.value = 'late@example.com'
  context.$refs.passwordInput.inputRef.value = 'late-password'

  LoginPage.methods.handleSubmit.call(context)

  assert.equal(context.form.email, 'late@example.com')
  assert.equal(context.form.password, 'late-password')
})

test('keeps route-provided email and already-entered credentials', () => {
  const context = createLoginContext({
    email: 'typed@example.com', // 已有的表单邮箱
    password: 'typed-password', // 已有的表单密码
    queryEmail: 'route@example.com', // 新路由预填邮箱优先
  })

  LoginPage.mounted.call(context)
  LoginPage.methods.syncAutofilledCredentials.call(context)

  assert.equal(context.form.email, 'route@example.com')
  assert.equal(context.form.password, 'typed-password')
})
