import { defineStore } from 'pinia'
import {
  deleteAiSession,
  getAiSessionMessages,
  getAiSessions,
  streamAiSearch,
} from '@/api/ai'
import { isAuthErrorHandled } from '@/utils/authSession'

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
    _abortController: null,
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
      this._streamingAssistantId = ''
    },

    abortStream() {
      if (this._abortController) {
        try {
          this._abortController.abort()
        } catch (_) {
          // ignore
        }
      }
      this._abortController = null
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
      if (this.streaming) this.abortStream()
      if (this.currentSessionId === id && this.messages.length) return

      this.currentSessionId = id
      this.streamError = ''
      this.messages = []
      this.messagesPage = 1
      this.messagesHasMore = false
      await this.loadMessages({ reset: true })
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
      if (this.streaming) throw new Error('正在生成回复，请稍候')

      this.streamError = ''
      this.draftQuery = ''

      const userMsg = {
        id: localId('user'),
        sessionId: this.currentSessionId || '',
        role: 'user',
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
        content: '',
        requestId: '',
        createTime: '',
        cites: [],
        streaming: true,
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
        await streamAiSearch(payload, {
          signal: controller.signal,
          onSession: (data) => {
            const rawId =
              data && typeof data === 'object'
                ? data.session_id != null
                  ? data.session_id
                  : data.sessionId
                : null
            const sid = rawId != null ? String(rawId).trim() : ''
            if (!sid) return
            this.currentSessionId = sid
            userMsg.sessionId = sid
            assistantMsg.sessionId = sid
            this.upsertSession({
              id: sid,
              title: text.length > 24 ? `${text.slice(0, 24)}…` : text,
            })
          },
          onCite: (data) => {
            const cite = normalizeCite(data)
            if (!cite) return
            const msg = this.messages.find((m) => m.id === assistantId)
            if (!msg) return
            if (msg.cites.some((c) => c.memeId === cite.memeId)) return
            msg.cites.push(cite)
          },
          onToken: (data) => {
            const chunk =
              data && typeof data === 'object'
                ? data.text != null
                  ? String(data.text)
                  : ''
                : String(data || '')
            if (!chunk) return
            const msg = this.messages.find((m) => m.id === assistantId)
            if (msg) msg.content += chunk
          },
          onDone: () => {
            const msg = this.messages.find((m) => m.id === assistantId)
            if (msg) {
              msg.streaming = false
              if (!msg.content.trim() && !msg.cites.length) {
                msg.content = '暂时没有生成内容，请换个说法再试。'
              }
            }
          },
          onError: (data) => {
            const message =
              data && typeof data === 'object'
                ? data.message || data.msg || '生成失败'
                : '生成失败'
            const msg = this.messages.find((m) => m.id === assistantId)
            if (msg) {
              msg.streaming = false
              msg.error = String(message)
              if (!msg.content) msg.content = String(message)
            }
            this.streamError = String(message)
          },
        })
      } catch (e) {
        if (e && e.name === 'AbortError') {
          const msg = this.messages.find((m) => m.id === assistantId)
          if (msg) {
            msg.streaming = false
            if (!msg.content.trim()) msg.content = '已停止生成'
          }
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
        this.streaming = false
        this._abortController = null
        this._streamingAssistantId = ''
        const msg = this.messages.find((m) => m.id === assistantId)
        if (msg) msg.streaming = false
      }
    },
  },
})
