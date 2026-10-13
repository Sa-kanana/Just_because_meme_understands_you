import test from 'node:test'
import assert from 'node:assert/strict'
import { handleAuthErrorIfExpired } from '../src/api/requestBusinessError.mjs'

test('业务拦截错误不应标记为已处理，供页面展示给用户', () => {
  const error = Object.assign(new Error('评论包含违规内容，无法发布'), { code: 400 })
  let logoutCalls = 0

  handleAuthErrorIfExpired(error, {
    isAuthExpiredError: () => false,
    handleFinalLogout: () => { logoutCalls += 1 },
    markAuthErrorHandled: (value) => {
      value.__authHandled = true
      return value
    },
  })

  assert.equal(error.message, '评论包含违规内容，无法发布')
  assert.equal(error.__authHandled, undefined)
  assert.equal(logoutCalls, 0)
})

test('会话过期错误仍由会话流程处理并标记', () => {
  const error = Object.assign(new Error('登录已过期'), { code: 1002 })
  let logoutCalls = 0

  handleAuthErrorIfExpired(error, {
    isAuthExpiredError: (value) => value.code === 1002,
    handleFinalLogout: () => { logoutCalls += 1 },
    markAuthErrorHandled: (value) => {
      value.__authHandled = true
      return value
    },
  })

  assert.equal(logoutCalls, 1)
  assert.equal(error.__authHandled, true)
})
