const assert = require('node:assert/strict')
const test = require('node:test')

const config = require('../vue.config.js')

test('filters only the browser ResizeObserver loop notification from the dev overlay', () => {
  const runtimeErrors = config.devServer.client.overlay.runtimeErrors

  assert.equal(typeof runtimeErrors, 'function')
  assert.equal(
    runtimeErrors(new Error('ResizeObserver loop completed with undelivered notifications.')),
    false,
  )
  assert.equal(runtimeErrors(new Error('TypeError: unexpected failure')), true)
})
