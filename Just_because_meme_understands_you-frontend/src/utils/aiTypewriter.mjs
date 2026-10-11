/** 创建逐字呈现状态，并从已显示文本继续，避免组件重建后从头播放。 */
export function createTypewriterState(initialVisibleText = '') {
  const visible = splitGraphemes(initialVisibleText)
  return { target: visible.slice(), visible, finished: false }
}

/** 将文本按用户可见的字素切分，避免拆开 emoji 和组合字符。 */
function splitGraphemes(text) {
  const value = String(text == null ? '' : text)
  if (typeof Intl !== 'undefined' && typeof Intl.Segmenter === 'function') {
    const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' })
    return Array.from(segmenter.segment(value), (item) => item.segment)
  }
  return Array.from(value)
}

/** 更新目标文本；若流内容回退或替换，只保留新旧文本共同的已显示前缀。 */
export function updateTypewriterTarget(state, text) {
  const nextTarget = splitGraphemes(text)
  let commonLength = 0
  while (
    commonLength < state.target.length
    && commonLength < nextTarget.length
    && state.target[commonLength] === nextTarget[commonLength]
  ) {
    commonLength += 1
  }

  if (commonLength < state.target.length) {
    state.visible = state.visible.slice(0, Math.min(commonLength, nextTarget.length))
  }
  state.target = nextTarget
}

/** 按指定字素数推进动画，返回本次新增的字素数。 */
export function revealTypewriter(state, count = 1) {
  const amount = Number.isFinite(Number(count)) ? Math.max(0, Math.floor(Number(count))) : 0
  const remaining = Math.max(0, state.target.length - state.visible.length)
  const revealed = Math.min(amount, remaining)
  if (revealed > 0) {
    state.visible.push(...state.target.slice(state.visible.length, state.visible.length + revealed))
  }
  return revealed
}

/** 结束动画时直接呈现完整目标文本，避免最终回答被留在缓冲区。 */
export function finishTypewriter(state) {
  state.visible = state.target.slice()
  state.finished = true
}

/** 标记服务端已完成，但保留尚未呈现的字素队列供界面继续播放。 */
export function markTypewriterComplete(state) {
  state.finished = true
}

/** 返回当前已经呈现的文本。 */
export function typewriterText(state) {
  return state.visible.join('')
}
