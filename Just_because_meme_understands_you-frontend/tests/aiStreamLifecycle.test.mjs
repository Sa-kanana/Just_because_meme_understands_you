import test from 'node:test'
import assert from 'node:assert/strict'
import {
  nextAiStreamRetryDelay,
  normalizeAiTaskStatus,
  shouldReconnectAiTask,
  stopAiStreamTask,
} from '../src/utils/aiStreamLifecycle.mjs'

test('uses capped exponential event reconnect delays', () => {
  assert.deepEqual([0, 1, 2, 3, 4, 8].map(nextAiStreamRetryDelay), [250, 500, 1000, 2000, 4000, 4000])
})

test('reconnects a running unexpired task after a recoverable transport error', () => {
  assert.equal(shouldReconnectAiTask({
    status: 'GENERATING',
    error: new TypeError('network disconnected'),
    expiresAt: 2000,
    now: 1000,
  }), true)
})

test('does not reconnect after abort, authentication failure, explicit stop, terminal state, or expiry', () => {
  const abort = new Error('aborted')
  abort.name = 'AbortError'
  const auth = new Error('expired')
  auth.status = 401
  const expired = new Error('task expired')
  expired.status = 404
  const cases = [
    { status: 'GENERATING', error: abort, expiresAt: 2000, now: 1000 },
    { status: 'GENERATING', error: auth, expiresAt: 2000, now: 1000 },
    { status: 'GENERATING', error: expired, expiresAt: 2000, now: 1000 },
    { status: 'GENERATING', stopped: true, expiresAt: 2000, now: 1000 },
    { status: 'COMPLETED', expiresAt: 2000, now: 1000 },
    { status: 'FAILED', expiresAt: 2000, now: 1000 },
    { status: 'STOPPED', expiresAt: 2000, now: 1000 },
    { status: 'GENERATING', expiresAt: 1000, now: 1000 },
  ]
  for (const input of cases) assert.equal(shouldReconnectAiTask(input), false)
})

test('normalizes the previous Redis task status names for active tasks', () => {
  assert.equal(normalizeAiTaskStatus('RUNNING'), 'GENERATING')
  assert.equal(normalizeAiTaskStatus('CANCELLED'), 'STOPPED')
  assert.equal(normalizeAiTaskStatus('COMPLETED'), 'COMPLETED')
})

test('stops reading events before requesting task cancellation', async () => {
  const steps = []
  const task = await stopAiStreamTask({
    taskId: 'task-1',
    status: 'GENERATING',
    stopReceiving: () => steps.push('stop-receiving'),
    requestStop: async (taskId) => {
      steps.push(`request-stop:${taskId}`)
      return { status: 'STOPPED' }
    },
  })

  assert.deepEqual(steps, ['stop-receiving', 'request-stop:task-1'])
  assert.equal(task.status, 'STOPPED')
})
