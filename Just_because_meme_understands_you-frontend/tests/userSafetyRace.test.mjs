import test from 'node:test'
import assert from 'node:assert/strict'
import { canApplySafetyStatus } from '../src/utils/userSafety.mjs'

test('ignores a safety response after the profile target changes', () => {
  assert.equal(canApplySafetyStatus({
    requestTargetId: '12', requestViewerId: '5', currentTargetId: '13', currentViewerId: '5',
    requestEpoch: 1, currentEpoch: 1,
  }), false)
})

test('ignores a safety response after the logged-in viewer changes', () => {
  assert.equal(canApplySafetyStatus({
    requestTargetId: '12', requestViewerId: '5', currentTargetId: '12', currentViewerId: '6',
    requestEpoch: 1, currentEpoch: 1,
  }), false)
})

test('ignores an older safety response after a newer refresh starts', () => {
  assert.equal(canApplySafetyStatus({
    requestTargetId: '12', requestViewerId: '5', currentTargetId: '12', currentViewerId: '5',
    requestEpoch: 1, currentEpoch: 2,
  }), false)
})
