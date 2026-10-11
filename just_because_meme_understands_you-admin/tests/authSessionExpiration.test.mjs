import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createSessionExpiredHandler,
  resetSessionExpiredHandler,
} from '../src/utils/authSessionLifecycle.mjs'

test('expires the session once, opens the login flow, and resets after a new login', () => {
  resetSessionExpiredHandler()
  const originalStorage = global.sessionStorage
  const storedValues = new Map()
  global.sessionStorage = {
    setItem(key, value) {
      storedValues.set(key, value)
    },
  }

  const currentRoute = { value: { name: 'knowledge', fullPath: '/knowledge?page=2' } }
  const router = {
    currentRoute,
    replacements: [],
    replace(location) {
      this.replacements.push(location)
      return Promise.resolve()
    },
  }
  const authStore = { clearCount: 0, clearAuthState() { this.clearCount += 1 } }
  const notifications = []
  const handleExpired = createSessionExpiredHandler({
    authStore,
    router,
    notify: (message) => notifications.push(message),
  })

  try {
    const firstError = new Error('expired')
    handleExpired(firstError)
    handleExpired(new Error('another request also received 401'))

    assert.equal(firstError.__authHandled, true)
    assert.equal(authStore.clearCount, 1)
    assert.equal(notifications.length, 1)
    assert.equal(notifications[0], '登录已过期，请重新登录')
    assert.deepEqual(router.replacements, [
      { name: 'login', query: { redirect: '/knowledge?page=2', reason: 'expired' } },
    ])
    assert.equal(storedValues.get('admin_post_login_redirect'), '/knowledge?page=2')

    resetSessionExpiredHandler()
    handleExpired(new Error('a later session expired'))
    assert.equal(authStore.clearCount, 2)
    assert.equal(notifications.length, 2)
  } finally {
    resetSessionExpiredHandler()
    if (originalStorage === undefined) delete global.sessionStorage
    else global.sessionStorage = originalStorage
  }
})
