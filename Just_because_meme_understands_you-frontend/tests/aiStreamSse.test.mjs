import test from 'node:test'
import assert from 'node:assert/strict'
import { readAiStreamSse } from '../src/utils/aiStreamSse.mjs'

function responseFromChunks(chunks) {
  const encoder = new TextEncoder()
  const bytes = chunks.map((chunk) => typeof chunk === 'string' ? encoder.encode(chunk) : chunk)
  return {
    ok: true,
    body: new ReadableStream({
      start(controller) {
        bytes.forEach((chunk) => controller.enqueue(chunk))
        controller.close()
      },
    }),
  }
}

test('parses split UTF-8 and event fields across network chunks', async () => {
  const encoded = new TextEncoder().encode('id: 7\nevent: token\ndata: {"text":"你好"}\n\n')
  const split = encoded.findIndex((byte, index) => index > 0 && byte >= 0x80)
  const events = []

  await readAiStreamSse(responseFromChunks([encoded.slice(0, split + 1), encoded.slice(split + 1)]), {
    onToken: (data) => events.push(data),
    onEvent: (event) => events.push(event),
  })

  assert.deepEqual(events, [{ text: '你好' }, { id: '7', event: 'token', data: { text: '你好' } }])
})

test('parses CRLF, JSON data, and multiple events in one chunk', async () => {
  const events = []
  const frame = [
    'id: 10\r\nevent: session\r\ndata: {"session_id":"12"}\r\n\r\n',
    'id: 11\r\nevent: cite\r\ndata: {"meme_id":"m-1"}\r\n\r\n',
  ].join('')

  await readAiStreamSse(responseFromChunks([frame]), {
    onSession: (data) => events.push(data),
    onCite: (data) => events.push(data),
  })

  assert.deepEqual(events, [{ session_id: '12' }, { meme_id: 'm-1' }])
})

test('joins multi-line data and dispatches a final complete event at EOF', async () => {
  const events = []
  await readAiStreamSse(responseFromChunks(['id: 14\nevent: error\ndata: first\ndata: second']), {
    onError: (data) => events.push(data),
    onEvent: (event) => events.push(event),
  })

  assert.deepEqual(events, ['first\nsecond', {
    id: '14', event: 'error', data: 'first\nsecond',
  }])
})

test('advances the event cursor only after the event handler succeeds', async () => {
  const cursorEvents = []
  await assert.rejects(
    readAiStreamSse(responseFromChunks(['id: 15\nevent: token\ndata: {"text":"x"}\n\n']), {
      onToken: () => { throw new Error('consumer failed') },
      onEvent: (event) => cursorEvents.push(event),
    }),
    /consumer failed/
  )
  assert.deepEqual(cursorEvents, [])
})

test('dispatches meta, done, and error payloads to their typed handlers', async () => {
  const received = []
  const frame = [
    'event: meta\ndata: {"request_id":"r1"}\n\n',
    'event: done\ndata: {"finish_reason":"stop"}\n\n',
    'event: error\ndata: {"code":"upstream_error"}\n\n',
  ].join('')
  await readAiStreamSse(responseFromChunks([frame]), {
    onMeta: (data) => received.push(data.request_id),
    onDone: (data) => received.push(data.finish_reason),
    onError: (data) => received.push(data.code),
  })
  assert.deepEqual(received, ['r1', 'stop', 'upstream_error'])
})
