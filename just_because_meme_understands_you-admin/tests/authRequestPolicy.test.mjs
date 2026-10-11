import assert from 'node:assert/strict'
import test from 'node:test'
import { shouldForceLogoutOnUnauthorized } from '../src/utils/authRequestPolicy.mjs'

test('登录态请求收到 401 时立即强制退出', () => {
  assert.equal(shouldForceLogoutOnUnauthorized({ status: 401 }, {}), true)
})

test('业务响应明确返回登录过期码时立即强制退出', () => {
  assert.equal(
    shouldForceLogoutOnUnauthorized({ status: 200, data: { code: 1002 } }, {}),
    true,
  )
})

test('跳过认证续期的登录请求收到 401 时不清除会话', () => {
  assert.equal(
    shouldForceLogoutOnUnauthorized({ status: 401 }, { __skipAuthRefresh: true }),
    false,
  )
})

test('不携带认证信息的公开请求收到 401 时不清除会话', () => {
  assert.equal(
    shouldForceLogoutOnUnauthorized({ status: 401 }, { __skipAuthHeader: true }),
    false,
  )
})

test('非 401 响应不会强制退出', () => {
  assert.equal(shouldForceLogoutOnUnauthorized({ status: 403 }, {}), false)
})
