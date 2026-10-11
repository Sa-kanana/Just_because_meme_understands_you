import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createTypewriterState,
  finishTypewriter,
  markTypewriterComplete,
  revealTypewriter,
  typewriterText,
  updateTypewriterTarget,
} from '../src/utils/aiTypewriter.mjs'

test('reveals streamed text in small grapheme-safe steps', () => {
  const state = createTypewriterState()
  updateTypewriterTarget(state, 'A👨‍👩‍👧‍👦你e\u0301')

  assert.equal(revealTypewriter(state, 2), 2)
  assert.equal(typewriterText(state), 'A👨‍👩‍👧‍👦')
  assert.equal(revealTypewriter(state, 1), 1)
  assert.equal(typewriterText(state), 'A👨‍👩‍👧‍👦你')
})

test('extends the target as more stream chunks arrive', () => {
  const state = createTypewriterState()
  updateTypewriterTarget(state, '第一')
  revealTypewriter(state, 1)
  updateTypewriterTarget(state, '第一段回答')
  revealTypewriter(state, 20)

  assert.equal(typewriterText(state), '第一段回答')
})

test('resumes from text already displayed before the component was recreated', () => {
  const state = createTypewriterState('已经显示')
  updateTypewriterTarget(state, '已经显示的部分，继续回答')

  assert.equal(typewriterText(state), '已经显示')
  assert.equal(revealTypewriter(state, 2), 2)
  assert.equal(typewriterText(state), '已经显示的部')
})

test('reconciles a changed target without retaining text outside the new answer', () => {
  const state = createTypewriterState()
  updateTypewriterTarget(state, '旧回答')
  revealTypewriter(state, 2)
  updateTypewriterTarget(state, '新回答')

  assert.equal(typewriterText(state), '')
  revealTypewriter(state, 1)
  assert.equal(typewriterText(state), '新')
})

test('finishes by immediately exposing all remaining text', () => {
  const state = createTypewriterState()
  updateTypewriterTarget(state, '完整回答')
  revealTypewriter(state, 1)
  finishTypewriter(state)

  assert.equal(typewriterText(state), '完整回答')
})

test('marks the stream complete without skipping characters still in the display queue', () => {
  const state = createTypewriterState()
  updateTypewriterTarget(state, '继续呈现')
  revealTypewriter(state, 1)
  markTypewriterComplete(state)

  assert.equal(state.finished, true)
  assert.equal(typewriterText(state), '继')
  revealTypewriter(state, 3)
  assert.equal(typewriterText(state), '继续呈现')
})
