const assert = require('node:assert/strict')
const Module = require('node:module')
const path = require('node:path')
const test = require('node:test')
const babel = require('@babel/core')
const { createPinia, setActivePinia } = require('pinia')

const storePath = path.resolve(__dirname, '../src/stores/aiChat.js')

/** 使用真实 Pinia 加载 store，并只替换网络边界以复现任务竞态。 */
function loadAiChatStore(apiOverrides = {}) {
  const api = {
    deleteAiSession: async () => {},
    cancelAiSearchTask: async () => null,
    createAiSearchTask: async () => null,
    getCurrentAiSearchTask: async () => null,
    getAiSessionMessages: async () => ({ list: [], hasMore: false }),
    getAiSessions: async () => ({ list: [], page: 1, hasMore: false }),
    streamAiSearchTask: async () => {},
    ...apiOverrides,
  }
  const originalLoad = Module._load
  Module._load = function load(request, parent, isMain) {
    if (request === '@/api/ai') return api
    if (request === '@/utils/authSession') return { isAuthErrorHandled: () => false }
    if (request === '@/utils/aiStreamLifecycle.mjs') {
      return {
        nextAiStreamRetryDelay: () => 0,
        shouldReconnectAiTask: () => false,
        stopAiStreamTask: async ({ taskId, stopReceiving, requestStop }) => {
          if (typeof stopReceiving === 'function') stopReceiving()
          return requestStop(taskId)
        },
      }
    }
    return originalLoad.call(this, request, parent, isMain)
  }

  try {
    const source = babel.transformFileSync(storePath, {
      plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')],
    }).code
    const loaded = new Module(storePath, module)
    loaded.filename = storePath
    loaded.paths = Module._nodeModulePaths(path.dirname(storePath))
    loaded._compile(source, storePath)
    return { useAiChatStore: loaded.exports.useAiChatStore, api }
  } finally {
    Module._load = originalLoad
  }
}

function createStore(apiOverrides = {}) {
  setActivePinia(createPinia())
  const loaded = loadAiChatStore(apiOverrides)
  return { store: loaded.useAiChatStore(), api: loaded.api }
}

test('recovered completed submission reloads the persisted answer', async () => {
  let requestId = ''
  const { store } = createStore({
    createAiSearchTask: async (payload) => {
      requestId = payload.requestId
      throw new Error('response lost')
    },
    getCurrentAiSearchTask: async () => ({
      taskId: 'task-completed',
      sessionId: 'session-1',
      requestId,
      status: 'COMPLETED',
      expiresAtMillis: Date.now() + 60000,
    }),
    getAiSessionMessages: async () => ({
      list: [
        { id: 'saved-user', role: 'user', content: '查找梗', requestId },
        { id: 'saved-answer', role: 'assistant', content: '这是完整结果。', requestId },
      ],
      hasMore: false,
    }),
  })

  await store.sendQuery('查找梗')

  assert.equal(store.taskStatus, 'COMPLETED')
  assert.equal(store.currentSessionId, 'session-1')
  assert.equal(store.messages.find((message) => message.role === 'assistant').content, '这是完整结果。')
  assert.equal(store.streaming, false)
})

test('stream completion keeps the locally streamed assistant message for gradual rendering', async () => {
  let persistedMessageLoads = 0
  const { store } = createStore({
    createAiSearchTask: async (payload) => ({
      taskId: 'task-live',
      sessionId: 'session-live',
      requestId: payload.requestId,
      status: 'GENERATING',
      expiresAtMillis: Date.now() + 60000,
    }),
    streamAiSearchTask: async (_taskId, _cursor, handlers) => {
      handlers.onToken({ text: '第一段' }, 1)
      handlers.onToken({ text: '第二段' }, 2)
      await handlers.onDone({ finish_reason: 'stop' }, 3)
    },
    getAiSessionMessages: async () => {
      persistedMessageLoads += 1
      return { list: [], hasMore: false }
    },
  })

  await store.sendQuery('查找一个梗')

  const answer = store.messages.find((message) => message.role === 'assistant')
  assert.equal(answer.content, '第一段第二段')
  assert.equal(answer.streaming, false)
  assert.equal(answer.typewriter, true)
  assert.equal(persistedMessageLoads, 0)
})

test('resuming the same running task keeps its event cursor and partial answer', async () => {
  let resumedCursor = null
  const { store } = createStore({
    getCurrentAiSearchTask: async () => ({
      taskId: 'task-resume',
      sessionId: 'session-resume',
      requestId: 'request-resume',
      status: 'GENERATING',
      expiresAtMillis: Date.now() + 60000,
    }),
    streamAiSearchTask: async (_taskId, cursor, handlers) => {
      resumedCursor = cursor
      handlers.onToken({ text: '，这是续写。' }, 8)
      handlers.onEvent({ id: '8' })
      handlers.onDone({}, 9)
      handlers.onEvent({ id: '9' })
    },
  })
  const partialAnswer = {
    id: 'answer-resume',
    role: 'assistant',
    content: '刚才已经显示',
    displayedContent: '刚才已经',
    requestId: 'request-resume',
    streaming: true,
    typewriter: true,
    cites: [],
    error: '',
  }
  store.taskId = 'task-resume'
  store.requestId = 'request-resume'
  store.taskStatus = 'GENERATING'
  store.taskSessionId = 'session-resume'
  store.currentSessionId = 'session-resume'
  store.lastEventId = 7
  store.messages = [
    { id: 'question-resume', role: 'user', content: '继续回答', requestId: 'request-resume' },
    partialAnswer,
  ]

  await store.resumeCurrentTask()
  await store._taskReaderPromise

  assert.equal(resumedCursor, 7)
  assert.equal(store.messages.find((message) => message.id === 'answer-resume').content, '刚才已经显示，这是续写。')
  assert.equal(store.messages.find((message) => message.id === 'answer-resume').displayedContent, '刚才已经')
})

test('stop racing with completion reloads the answer without marking it stopped', async () => {
  const { store } = createStore({
    cancelAiSearchTask: async () => ({
      taskId: 'task-race',
      sessionId: 'session-1',
      requestId: 'request-1',
      status: 'COMPLETED',
      expiresAtMillis: Date.now() + 60000,
    }),
    getAiSessionMessages: async () => ({
      list: [
        { id: 'saved-user', role: 'user', content: '查找梗', requestId: 'request-1' },
        { id: 'saved-answer', role: 'assistant', content: '已经完成的结果。', requestId: 'request-1' },
      ],
      hasMore: false,
    }),
  })
  store.taskId = 'task-race'
  store.requestId = 'request-1'
  store.taskStatus = 'GENERATING'
  store.taskSessionId = 'session-1'
  store.currentSessionId = 'session-1'
  store._streamingAssistantId = 'temporary-answer'
  store.messages = [{
    id: 'temporary-answer', role: 'assistant', content: '已经完成的结果。', error: '', streaming: true,
  }]

  const task = await store.stopCurrentTask()

  const answer = store.messages.find((message) => message.role === 'assistant')
  assert.equal(task.status, 'COMPLETED')
  assert.equal(store.taskStatus, 'COMPLETED')
  assert.equal(answer.content, '已经完成的结果。')
  assert.equal(answer.error, '')
  assert.equal(answer.streaming, false)
})

test('confirmed user pause keeps partial output and marks it as paused', async () => {
  const { store } = createStore({
    cancelAiSearchTask: async () => ({
      taskId: 'task-cancelled',
      sessionId: 'session-1',
      requestId: 'request-1',
      status: 'STOPPED',
      expiresAtMillis: Date.now() + 60000,
    }),
  })
  store.taskId = 'task-cancelled'
  store.requestId = 'request-1'
  store.taskStatus = 'GENERATING'
  store.taskSessionId = 'session-1'
  store.currentSessionId = 'session-1'
  store._streamingAssistantId = ''
  store.messages = [{
    id: 'temporary-answer', role: 'assistant', requestId: 'request-1', content: '部分结果', error: '', streaming: true,
  }]

  await store.stopCurrentTask()

  assert.equal(store.taskStatus, 'STOPPED')
  assert.equal(store.messages[0].content, '部分结果')
  assert.equal(store.messages[0].paused, true)
  assert.equal(store.messages[0].error, '')
  assert.equal(store.messages[0].streaming, false)
})
