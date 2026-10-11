const EVENT_HANDLERS = Object.freeze({
  session: 'onSession',
  meta: 'onMeta',
  cite: 'onCite',
  token: 'onToken',
  done: 'onDone',
  error: 'onError',
})

/** 读取可恢复 AI 任务的 SSE 响应，并在事件处理成功后通知调用方推进游标。 */
export async function readAiStreamSse(response, handlers = {}) {
  if (!response || !response.body || typeof response.body.getReader !== 'function') {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = ''
  let eventId = ''
  let lastEventId = ''
  let dataLines = []
  let streamFinished = false

  const dispatch = async () => {
    if (!dataLines.length) {
      eventName = ''
      return
    }
    const raw = dataLines.join('\n')
    let data = raw
    try {
      data = JSON.parse(raw)
    } catch {
      // 非 JSON 事件数据以原始字符串交给事件处理函数。
    }

    const resolvedEvent = resolveEventName(eventName, data)
    const handlerName = EVENT_HANDLERS[resolvedEvent]
    if (handlerName && typeof handlers[handlerName] === 'function') {
      await handlers[handlerName](data, eventId || lastEventId)
    }
    if (typeof handlers.onEvent === 'function') {
      await handlers.onEvent({
        id: eventId || lastEventId,
        event: resolvedEvent,
        data,
      })
    }
    if (eventId) lastEventId = eventId
    eventId = ''
    eventName = ''
    dataLines = []
  }

  const processLine = async (rawLine) => {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (!line) {
      await dispatch()
      return
    }
    if (line.startsWith(':')) return

    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let value = separator < 0 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'event') eventName = value
    else if (field === 'id' && !value.includes('\0')) eventId = value
    else if (field === 'data') dataLines.push(value)
  }

  try {
    while (!streamFinished) {
      const { done, value } = await reader.read()
      if (done) {
        streamFinished = true
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let newline = buffer.indexOf('\n')
      while (newline >= 0) {
        await processLine(buffer.slice(0, newline))
        buffer = buffer.slice(newline + 1)
        newline = buffer.indexOf('\n')
      }
    }

    buffer += decoder.decode()
    if (buffer) await processLine(buffer)
    if (dataLines.length) await dispatch()
    return lastEventId
  } catch (error) {
    try {
      await reader.cancel(error)
    } catch {
      // 连接可能已由 AbortController 关闭。
    }
    throw error
  } finally {
    reader.releaseLock()
  }
}

function resolveEventName(eventName, data) {
  if (eventName && eventName !== 'message') return eventName
  if (!data || typeof data !== 'object') return eventName || 'message'
  if (data.session_id != null || data.sessionId != null) return 'session'
  if (Array.isArray(data.retrieved) || data.request_id != null || data.requestId != null) return 'meta'
  if (data.meme_id != null || data.memeId != null) return 'cite'
  if (data.text != null) return 'token'
  if (data.finish_reason != null) return 'done'
  if (data.code != null && (data.message != null || data.msg != null)) return 'error'
  return eventName || 'message'
}
