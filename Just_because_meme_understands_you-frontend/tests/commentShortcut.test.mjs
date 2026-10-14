import test from 'node:test'
import assert from 'node:assert/strict'
import { isCommentSubmitShortcut } from '../src/utils/commentShortcut.mjs'

test('识别 Ctrl+Enter 和 Meta+Enter 发送快捷键', () => {
  assert.equal(isCommentSubmitShortcut({
    key: 'Enter', // 触发的键名。
    ctrlKey: true, // 是否按下 Ctrl 键。
  }), true)
  assert.equal(isCommentSubmitShortcut({
    key: 'Enter', // 触发的键名。
    metaKey: true, // 是否按下 Mac 的 Command 键。
  }), true)
})

test('普通 Enter、其他按键和输入法组合期间的 Enter 不发送', () => {
  assert.equal(isCommentSubmitShortcut({
    key: 'Enter', // 普通 Enter 应保留文本换行行为。
  }), false)
  assert.equal(isCommentSubmitShortcut({
    key: 'a', // 非 Enter 按键不触发发送。
    ctrlKey: true, // 即使按住 Ctrl，也必须同时按 Enter。
  }), false)
  assert.equal(isCommentSubmitShortcut({
    key: 'Enter', // 输入法确认键仍可能是 Enter。
    ctrlKey: true, // 同时按下 Ctrl。
    isComposing: true, // 输入法组合过程中不应发送。
  }), false)
})
