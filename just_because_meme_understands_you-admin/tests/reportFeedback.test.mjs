import test from 'node:test'
import assert from 'node:assert/strict'
import { resolutionSuccessMessage } from '../src/utils/reportFeedback.mjs'

test('uses a matching success message for every report resolution', () => {
  assert.equal(resolutionSuccessMessage('OFFLINED'), '梗已下架')
  assert.equal(resolutionSuccessMessage('COMMENT_HIDDEN'), '评论已隐藏')
  assert.equal(resolutionSuccessMessage('DISMISSED'), '举报已驳回')
})
