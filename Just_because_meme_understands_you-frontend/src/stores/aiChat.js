import { defineStore } from 'pinia'
import {
  deleteAiSession,
  cancelAiSearchTask,
  createAiSearchTask,
  getCurrentAiSearchTask,
  getAiSessionMessages,
  getAiSessions,
  streamAiSearchTask,
} from '@/api/ai'
import { isAuthErrorHandled } from '@/utils/authSession'
import {
  nextAiStreamRetryDelay,
  shouldReconnectAiTask,
  stopAiStreamTask,
} from '@/utils/aiStreamLifecycle.mjs'

function localId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function normalizeCite(raw) {
  if (!raw || typeof raw !== 'object') return null
  const memeId = raw.meme_id != null ? String(raw.meme_id).trim()
    : raw.memeId != null ? String(raw.memeId).trim()
      : ''
  if (!memeId) return null
  return {
    memeId,
    score: Number.isFinite(Number(raw.score)) ? Number(raw.score) : 0,
    title: raw.title != null ? String(raw.title) : '',
  }
}

export const useAiChatStore = defineStore('aiChat', {
  state: () => ({
    sessions: [],
    sessionsPage: 1,
    sessionsHasMore: false,
    sessionsLoading: false,
    sessionsLoadingMore: false,

    currentSessionId: '',
    messages: [],
    messagesPage: 1,
    messagesHasMore: false,
    messagesLoading: false,
    messagesLoadingMore: false,

    streaming: false,
    streamError: '',
    draftQuery: '',
    /** 当前用户的后台 AI 任务 ID，可在切页或刷新后恢复。 */
    taskId: '',
    /** 当前后台任务对应的幂等请求 ID。 */
    requestId: '',
    /** 当前后台任务的服务端生命周期状态。 */
    taskStatus: '',
    /** 已成功处理的最后一个 SSE 事件 ID。 */
    lastEventId: 0,
    /** 当前后台任务的 Unix 毫秒过期时间。 */
    taskExpiresAtMillis: 0,
    /** 当前后台任务所属会话，防止事件写入其他会话。 */
    taskSessionId: '',
    _abortController: null,
    /** 仅负责读取事件的浏览器连接控制器，不控制后台生成任务。 */
    _eventAbortController: null,
    /** 阻止同一 Pinia 实例重复创建任务事件读取器。 */
    _taskReaderPromise: null,
    /** 显式停止请求期间禁止自动重连。 */
    _stopRequested: false,
    /** 当前临时助手消息的前端 ID，不属于持久化消息表。 */
    _streamingAssistantId: '',
  }),

  getters: {
    currentSession(state) {
      const id = String(state.currentSessionId || '').trim()
      if (!id) return null
      return state.sessions.find((s) => s.id === id) || null
    },
    hasMessages(state) {
      return state.messages.length > 0
    },
  },

  actions: {
    reset() {
      this.abortStream()
      this.sessions = []
      this.sessionsPage = 1
      this.sessionsHasMore = false
      this.sessionsLoading = false
      this.sessionsLoadingMore = false
      this.currentSessionId = ''
      this.messages = []
      this.messagesPage = 1
      this.messagesHasMore = false
      this.messagesLoading = false
      this.messagesLoadingMore = false
      this.streaming = false
      this.streamError = ''
      this.draftQuery = ''
      this.taskId = ''
      this.requestId = ''
      this.taskStatus = ''
      this.lastEventId = 0
      this.taskExpiresAtMillis = 0
      this.taskSessionId = ''
      this._eventAbortController = null
      this._taskReaderPromise = null
      this._stopRequested = false
      this._streamingAssistantId = ''
    },

    abortStream() {
      this.detachTaskEvents()
    },

    detachTaskEvents() {
      if (this._eventAbortController) {
        try {
          this._eventAbortController.abort()
        } catch (_) {
          // 已经关闭的事件读取器无需再次处理。
        }
      }
      this._eventAbortController = null
      this._abortController = null
      this._taskReaderPromise = null
      this.streaming = false
      this._streamingAssistantId = ''
    },

    startNewChat() {
      this.abortStream()
      this.currentSessionId = ''
      this.messages = []
      this.messagesPage = 1
      this.messagesHasMore = false
      this.streamError = ''
    },

    async loadSessions({ reset = true } = {}) {
      if (reset) {
        if (this.sessionsLoading) return
        this.sessionsLoading = true
        this.sessionsPage = 1
      } else {
        if (this.sessionsLoadingMore || !this.sessionsHasMore) return
        this.sessionsLoadingMore = true
      }

      const page = reset ? 1 : this.sessionsPage + 1
      try {
        const data = await getAiSessions({ page, size: 20 })
        const list = data.list || []
        this.sessions = reset ? list : [...this.sessions, ...list]
        this.sessionsPage = data.page || page
        this.sessionsHasMore = Boolean(data.hasMore)
      } finally {
        this.sessionsLoading = false
        this.sessionsLoadingMore = false
      }
    },

    async selectSession(sessionId) {
      const id = sessionId != null ? String(sessionId).trim() : ''
      if (!id) return
      const returningToTaskSession = this.taskSessionId === id && Boolean(this.taskId)
      if (this.currentSessionId === id && this.messages.length) {
        if (this.taskStatus === 'GENERATING' && this.taskSessionId === id && this.taskId && !this._taskReaderPromise) {
          this.ensureAssistantMessage()
          this.startTaskReader()
        }
        return
      }

      this.detachTaskEvents()
      this.currentSessionId = id
      this.streamError = ''
      this.messages = []
      this.messagesPage = 1
      this.messagesHasMore = false
      await this.loadMessages({ reset: true })
      if (returningToTaskSession && ['GENERATING', 'FAILED'].includes(this.taskStatus)) {
        // 会话切换会重载持久化消息；从零回放仍未落库的流式回答。
        this.lastEventId = 0
        if (this.taskStatus === 'GENERATING') {
          this.messages = this.messages.filter((message) =>
            !(message.role === 'assistant' && message.requestId === this.requestId))
        }
        this.ensureAssistantMessage()
        this.startTaskReader()
      }
    },

    async loadMessages({ reset = true } = {}) {
      const sessionId = String(this.currentSessionId || '').trim()
      if (!sessionId) return
      // 后端消息按时间升序分页；会话页一次拉齐（上限 500）以保证聊天时序正确
      if (!reset) return
      if (this.messagesLoading) return

      this.messagesLoading = true
      this.messagesPage = 1
      this.messagesHasMore = false
      try {
        const all = []
        let page = 1
        let hasMore = true
        while (hasMore && page <= 10) {
          const data = await getAiSessionMessages(sessionId, { page, size: 50 })
          const list = (data.list || []).map((m) => ({
            ...m,
            cites: [],
            streaming: false,
            paused: m.status === 'STOPPED',
            error: '',
          }))
          all.push(...list)
          hasMore = Boolean(data.hasMore)
          this.messagesPage = data.page || page
          page += 1
        }
        this.messages = all
        this.messagesHasMore = false
      } finally {
        this.messagesLoading = false
        this.messagesLoadingMore = false
      }
    },

    async removeSession(sessionId) {
      const id = sessionId != null ? String(sessionId).trim() : ''
      if (!id) return
      await deleteAiSession(id)
      this.sessions = this.sessions.filter((s) => s.id !== id)
      if (this.taskSessionId === id) {
        this.detachTaskEvents()
        this.taskStatus = 'STOPPED'
      }
      if (this.currentSessionId === id) {
        this.startNewChat()
      }
    },

    upsertSession(session) {
      if (!session || !session.id) return
      const id = String(session.id)
      const idx = this.sessions.findIndex((s) => s.id === id)
      const next = {
        id,
        title: session.title || '新对话',
        createTime: session.createTime || '',
        updateTime: session.updateTime || new Date().toISOString(),
      }
      if (idx >= 0) {
        const merged = { ...this.sessions[idx], ...next }
        this.sessions.splice(idx, 1)
        this.sessions.unshift(merged)
      } else {
        this.sessions.unshift(next)
      }
    },

    /**
     * 发送问句并消费 SSE
     * @param {string} query
     */
    async sendQuery(query) {
      const text = query != null ? String(query).trim() : ''
      if (!text) throw new Error('请输入你想搜索的梗或场景')
      if (this.taskStatus === 'GENERATING') {
        throw new Error('当前有搜索仍在后台生成，请等待完成或先点击“停止生成”')
      }
      if (this.streaming) throw new Error('正在生成回复，请稍候')

      this.streamError = ''
      this.draftQuery = ''

      const userMsg = {
        id: localId('user'),
        sessionId: this.currentSessionId || '',
        role: 'user',
        status: 'COMPLETED',
        content: text,
        requestId: '',
        createTime: '',
        cites: [],
        streaming: false,
        error: '',
      }
      const assistantId = localId('assistant')
      const assistantMsg = {
        id: assistantId,
        sessionId: this.currentSessionId || '',
        role: 'assistant',
        status: 'GENERATING',
        content: '',
        requestId: '',
        createTime: '',
        cites: [],
        streaming: true,
        typewriter: true,
        /** 已经呈现给用户的正文，用于路由切换后接续逐字动画。 */
        displayedContent: '',
        /** 用户主动暂停任务后显示的状态标记。 */
        paused: false,
        error: '',
      }
      this.messages.push(userMsg, assistantMsg)
      this._streamingAssistantId = assistantId

      const controller = new AbortController()
      this._abortController = controller
      this.streaming = true

      const requestId = localId('req').replace(/[^a-zA-Z0-9]/g, '').slice(0, 32)
      userMsg.requestId = requestId
      assistantMsg.requestId = requestId

      const payload = { query: text, requestId }
      if (this.currentSessionId) {
        payload.sessionId = this.currentSessionId
      }

      try {
        let task
        try {
          task = await createAiSearchTask(payload)
        } catch (createError) {
          const existingTask = await getCurrentAiSearchTask().catch(() => null)
          if (existingTask && existingTask.requestId === requestId) task = existingTask
          else throw createError
        }
        this.acceptTask(task, requestId)
        this.applyTaskSession(task.sessionId, userMsg, assistantMsg, text)
        if (this.taskStatus === 'COMPLETED') {
          await this.loadMessages({ reset: true })
        } else {
          await this.startTaskReader()
        }
      } catch (e) {
        if (e && e.name === 'AbortError') {
          return
        }
        if (isAuthErrorHandled(e)) throw e
        const message = e && e.message ? e.message : 'AI 搜索失败'
        const msg = this.messages.find((m) => m.id === assistantId)
        if (msg) {
          msg.streaming = false
          msg.error = message
          if (!msg.content) msg.content = message
        }
        this.streamError = message
        throw e
      } finally {
        const msg = this.messages.find((m) => m.id === assistantId)
        if (this.taskStatus !== 'GENERATING') {
          this.streaming = false
          if (msg) msg.streaming = false
        }
      }
    },

    /** 在页面加载时从服务端发现任务，并恢复其会话和事件流。 */
    async resumeCurrentTask() {
      const task = await getCurrentAiSearchTask()
      if (!task) return null
      const taskRequestId = String(task.requestId || '')
      const isSameLocalTask = String(this.taskId || '') === String(task.taskId || '')
        && String(this.requestId || '') === taskRequestId
        && String(this.taskSessionId || '') === String(task.sessionId || '')
      const hasLocalAssistant = isSameLocalTask && this.messages.some((message) =>
        message.role === 'assistant' && String(message.requestId || '') === taskRequestId)
      const preserveLocalProgress = isSameLocalTask && hasLocalAssistant

      this.acceptTask(task, task.requestId, { preserveEventCursor: preserveLocalProgress })
      if (task.status !== 'COMPLETED' && !hasLocalAssistant) {
        this.messages = this.messages.filter((message) =>
          !(message.role === 'assistant' && message.requestId === this.requestId))
      }
      if (!task.sessionId) return task

      const sessionId = String(task.sessionId)
      if (!this.sessions.some((session) => session.id === sessionId)) {
        this.upsertSession({ id: sessionId, title: '新对话' })
      }
      if (this.currentSessionId !== sessionId || !this.messages.length) {
        await this.selectSession(sessionId)
        return task
      }
      if (task.status === 'COMPLETED') {
        await this.loadMessages({ reset: true })
        return task
      }
      this.ensureAssistantMessage()
      if (task.status === 'GENERATING' || task.status === 'FAILED') {
        this.startTaskReader()
      }
      return task
    },

    /** 保存服务端返回的任务身份，不保存业务问题正文到任务元数据。 */
    acceptTask(task, requestId = '', { preserveEventCursor = false } = {}) {
      this.taskId = task.taskId
      this.requestId = task.requestId || requestId
      this.taskStatus = task.status
      if (!preserveEventCursor) this.lastEventId = 0
      this.taskExpiresAtMillis = task.expiresAtMillis
      this.taskSessionId = task.sessionId || ''
      this._stopRequested = false
    },

    /** 把任务解析出的会话 ID 应用到当前两个临时消息。 */
    applyTaskSession(sessionId, userMsg, assistantMsg, query) {
      const id = sessionId != null ? String(sessionId).trim() : ''
      if (!id) return
      this.currentSessionId = id
      this.taskSessionId = id
      userMsg.sessionId = id
      assistantMsg.sessionId = id
      this.upsertSession({
        id,
        title: query.length > 24 ? `${query.slice(0, 24)}…` : query,
      })
    },

    /** 在对应用户问题后创建不会写入服务端的临时回答气泡。 */
    ensureAssistantMessage() {
      if (!this.taskId || !this.taskSessionId || this.currentSessionId !== this.taskSessionId) return null
      const existing = this.messages.find((message) => message.id === this._streamingAssistantId)
      if (existing) return existing
      const matchingAssistant = this.messages.find((message) =>
        message.role === 'assistant' && message.requestId === this.requestId)
      if (matchingAssistant) {
        matchingAssistant.typewriter = true
        this._streamingAssistantId = matchingAssistant.id
        return matchingAssistant
      }

      const userIndex = this.messages.findIndex((message) =>
        message.role === 'user' && message.requestId === this.requestId)
      if (userIndex < 0) return null
      const message = {
        id: localId('assistant'),
        sessionId: this.taskSessionId,
        role: 'assistant',
        status: 'GENERATING',
        content: '',
        requestId: this.requestId,
        createTime: '',
        cites: [],
        streaming: this.taskStatus === 'GENERATING',
        typewriter: true,
        /** 已经呈现给用户的正文，用于路由切换后接续逐字动画。 */
        displayedContent: '',
        /** 用户主动暂停任务后显示的状态标记。 */
        paused: false,
        error: '',
      }
      this.messages.splice(userIndex + 1, 0, message)
      this._streamingAssistantId = message.id
      return message
    },

    /** 判断事件是否仍在当前回放游标之后，避免断线竞态重复追加内容。 */
    isNewTaskEvent(eventId) {
      const id = Number(eventId)
      return !eventId || !Number.isSafeInteger(id) || id > this.lastEventId
    },

    /** 读取事件并在可恢复的传输错误后使用指数退避重新连接。 */
    startTaskReader() {
      if (this._taskReaderPromise) return this._taskReaderPromise
      if (!this.taskId || this.currentSessionId !== this.taskSessionId) return Promise.resolve()
      if (!['GENERATING', 'FAILED'].includes(this.taskStatus)) return Promise.resolve()
      const assistant = this.ensureAssistantMessage()
      if (!assistant) return Promise.resolve()

      const taskId = this.taskId
      const sessionId = this.taskSessionId
      const controller = new AbortController()
      this._eventAbortController = controller
      this._abortController = controller
      this.streaming = this.taskStatus === 'GENERATING'
      assistant.streaming = this.taskStatus === 'GENERATING'
      const readerPromise = this.consumeTaskEvents(taskId, sessionId, assistant.id, controller)
      this._taskReaderPromise = readerPromise
      const cleanup = () => {
        if (this._taskReaderPromise !== readerPromise) return
        this._taskReaderPromise = null
        this._eventAbortController = null
        this._abortController = null
        this.streaming = false
        const message = this.messages.find((item) => item.id === assistant.id)
        if (message) message.streaming = false
        if (this._streamingAssistantId === assistant.id) this._streamingAssistantId = ''
      }
      readerPromise.then(cleanup, cleanup)
      return readerPromise
    },

    /** 消费一次或多次 SSE 连接，确保事件只写入原会话且不重复追加。 */
    async consumeTaskEvents(taskId, sessionId, assistantId, controller) {
      let retryAttempt = 0
      while (this.taskId === taskId
        && this.taskSessionId === sessionId
        && this.currentSessionId === sessionId
        && !controller.signal.aborted) {
        try {
          await streamAiSearchTask(taskId, this.lastEventId, {
            signal: controller.signal,
            onSession: (data) => {
              const rawId = data && typeof data === 'object'
                ? data.session_id != null ? data.session_id : data.sessionId
                : null
              const resolvedSessionId = rawId != null ? String(rawId) : ''
              if (resolvedSessionId && resolvedSessionId === sessionId) {
                this.currentSessionId = resolvedSessionId
              }
            },
            onCite: (data, eventId) => {
              if (!this.isNewTaskEvent(eventId)) return
              const cite = normalizeCite(data)
              const message = this.messages.find((item) => item.id === assistantId)
              if (!cite || !message || message.cites.some((item) => item.memeId === cite.memeId)) return
              message.cites.push(cite)
            },
            onToken: (data, eventId) => {
              if (!this.isNewTaskEvent(eventId)) return
              const chunk = data && typeof data === 'object'
                ? data.text != null ? String(data.text) : ''
                : String(data || '')
              const message = this.messages.find((item) => item.id === assistantId)
              if (chunk && message) message.content += chunk
            },
            onDone: async (data, eventId) => {
              if (!this.isNewTaskEvent(eventId)) return
              this.taskStatus = 'COMPLETED'
              this.streamError = ''
              const message = this.messages.find((item) => item.id === assistantId)
              if (message) {
                message.status = 'COMPLETED'
                message.streaming = false
                if (!message.content.trim() && !message.cites.length) {
                  message.content = '暂时没有生成内容，请换个说法再试。'
                }
              }
              // 保留本地流式消息，让界面完成逐字呈现；重开会话时再读取持久化记录。
            },
            onError: (data, eventId) => {
              if (!this.isNewTaskEvent(eventId)) return
              const code = data && typeof data === 'object' ? String(data.code || '') : ''
              const messageText = data && typeof data === 'object'
                ? data.message || data.msg || '生成失败'
                : '生成失败'
              const stopped = code === 'task_stopped' || code === 'task_cancelled'
              this.taskStatus = stopped ? 'STOPPED'
                : code === 'task_expired' ? 'EXPIRED' : 'FAILED'
              const message = this.messages.find((item) => item.id === assistantId)
              if (message) {
                message.status = stopped ? 'STOPPED' : 'FAILED'
                message.streaming = false
                if (stopped) {
                  message.paused = true
                  message.error = ''
                } else {
                  message.error = String(messageText)
                  if (!message.content) message.content = String(messageText)
                }
              }
              this.streamError = stopped ? '' : String(messageText)
            },
            onEvent: (event) => {
              const id = Number(event.id)
              if (Number.isSafeInteger(id) && id <= this.lastEventId) return
              if (Number.isSafeInteger(id)) this.lastEventId = id
              retryAttempt = 0
            },
          })
          if (this.taskStatus !== 'GENERATING') break
          throw new Error('AI 搜索事件流已结束，等待任务完成后恢复')
        } catch (error) {
          if (error && error.name === 'AbortError') break
          if (!shouldReconnectAiTask({
            status: this.taskStatus,
            error,
            stopped: this._stopRequested || controller.signal.aborted,
            expiresAt: this.taskExpiresAtMillis,
          })) {
            if (Number(error && error.status) === 404 && this.taskStatus === 'GENERATING') {
              this.taskStatus = 'EXPIRED'
              const message = this.messages.find((item) => item.id === assistantId)
              if (message) {
                message.streaming = false
                message.error = '搜索任务已不可恢复，请重新发起搜索。'
              }
            }
            if (!isAuthErrorHandled(error) && this.taskStatus === 'GENERATING') {
              this.streamError = error && error.message ? error.message : 'AI 搜索连接失败'
            }
            break
          }
          const delay = nextAiStreamRetryDelay(retryAttempt)
          retryAttempt += 1
          await new Promise((resolve) => setTimeout(resolve, delay))
        }
      }

      if (this.taskStatus === 'GENERATING' && Date.now() >= this.taskExpiresAtMillis) {
        this.taskStatus = 'EXPIRED'
      }
      if (this.taskStatus === 'STOPPED') {
        const message = this.messages.find((item) => item.id === assistantId)
        if (message) {
          message.status = 'STOPPED'
          message.paused = true
          message.error = ''
        }
      }
    },

    /** 主动调用取消接口；页面离开只调用 detachTaskEvents。 */
    async stopCurrentTask() {
      if (!this.taskId || this.taskStatus !== 'GENERATING') {
        this.detachTaskEvents()
        return null
      }
      const assistantId = this._streamingAssistantId || this.messages.find((message) =>
        message.role === 'assistant' && message.requestId === this.requestId)?.id
      this._stopRequested = true
      try {
        const task = await stopAiStreamTask({
          taskId: this.taskId,
          status: this.taskStatus,
          stopReceiving: () => this.detachTaskEvents(),
          requestStop: cancelAiSearchTask,
        })
        if (!task) return null
        this.taskStatus = task.status
        if (task.status === 'COMPLETED' && this.currentSessionId === this.taskSessionId) {
          await this.loadMessages({ reset: true })
        } else if (task.status === 'STOPPED') {
          const message = this.messages.find((item) => item.id === assistantId)
          if (message) {
            message.status = 'STOPPED'
            message.streaming = false
            message.paused = true
            message.error = ''
          }
        }
        return task
      } catch (error) {
        this._stopRequested = false
        if (this.taskStatus === 'GENERATING') this.startTaskReader()
        throw error
      }
    },
  },
})
