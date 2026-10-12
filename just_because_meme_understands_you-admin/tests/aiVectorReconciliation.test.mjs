import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canResumeVectorReconciliation,
  isVectorReconciliationActive,
  vectorReconciliationJobPath,
  vectorReconciliationStatusLabel,
} from '../src/utils/aiVectorReconciliation.mjs'

test('only pending and running reconciliation jobs are active', () => {
  assert.equal(isVectorReconciliationActive('PENDING'), true)
  assert.equal(isVectorReconciliationActive('RUNNING'), true)
  assert.equal(isVectorReconciliationActive('FAILED'), false)
  assert.equal(isVectorReconciliationActive('COMPLETED'), false)
})

test('only failed reconciliation jobs can be resumed', () => {
  assert.equal(canResumeVectorReconciliation('FAILED'), true)
  assert.equal(canResumeVectorReconciliation('PENDING'), false)
  assert.equal(canResumeVectorReconciliation('RUNNING'), false)
  assert.equal(canResumeVectorReconciliation('COMPLETED'), false)
})

test('builds a safe job path and rejects invalid job IDs', () => {
  assert.equal(
    vectorReconciliationJobPath('2108897332320768001'),
    '/admin/ai/vector-index/reconciliation-jobs/2108897332320768001'
  )
  for (const jobId of ['', '0', '-1', '1/2', ' 3', null]) {
    assert.throws(() => vectorReconciliationJobPath(jobId))
  }
})

test('labels queue-oriented reconciliation statuses', () => {
  assert.equal(vectorReconciliationStatusLabel('PENDING'), '等待处理')
  assert.equal(vectorReconciliationStatusLabel('RUNNING'), '校准中')
  assert.equal(vectorReconciliationStatusLabel('FAILED'), '失败，可继续')
  assert.equal(vectorReconciliationStatusLabel('COMPLETED'), '已完成（事件已入队）')
})
