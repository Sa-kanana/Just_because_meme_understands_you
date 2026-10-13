/** 仅将已交给登录会话流程处理的过期错误标记为已处理。 */
export function handleAuthErrorIfExpired(error, handlers = {}) {
  const isExpired = typeof handlers.isAuthExpiredError === 'function'
    && handlers.isAuthExpiredError(error)
  const canHandle = typeof handlers.handleFinalLogout === 'function'

  if (!isExpired || !canHandle) return error

  handlers.handleFinalLogout(error)
  return typeof handlers.markAuthErrorHandled === 'function'
    ? handlers.markAuthErrorHandled(error)
    : error
}
