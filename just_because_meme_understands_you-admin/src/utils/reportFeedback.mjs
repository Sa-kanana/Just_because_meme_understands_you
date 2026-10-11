export function resolutionSuccessMessage(result) {
  if (result === 'OFFLINED') return '梗已下架'
  if (result === 'COMMENT_HIDDEN') return '评论已隐藏'
  return '举报已驳回'
}
