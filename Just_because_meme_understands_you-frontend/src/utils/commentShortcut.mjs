/** 判断键盘事件是否为发送评论的 Ctrl/⌘ + Enter 快捷键。 */
export function isCommentSubmitShortcut(event) {
  return Boolean(event?.key === 'Enter'
    && (event.ctrlKey || event.metaKey)
    && !event.isComposing)
}
