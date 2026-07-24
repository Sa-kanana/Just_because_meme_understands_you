<template>
  <div class="ai-page">
    <div class="ai-shell">
      <AiSessionSidebar
        :sessions="store.sessions"
        :current-session-id="store.currentSessionId"
        :loading="store.sessionsLoading"
        :loading-more="store.sessionsLoadingMore"
        :has-more="store.sessionsHasMore"
        :collapsed="sidebarCollapsed"
        @new-chat="onNewChat"
        @select="onSelectSession"
        @delete="onDeleteSession"
        @load-more="onLoadMoreSessions"
        @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
      />

      <section class="ai-main">
        <header class="ai-main__head">
          <div class="ai-main__titles">
            <h1 class="ai-main__title">
              {{ store.currentSession?.title || '新对话' }}
            </h1>
            <p class="ai-main__subtitle">站内梗检索 · 流式回答</p>
          </div>
        </header>

        <AiChatThread
          :messages="store.messages"
          :loading="store.messagesLoading"
          :loading-more="store.messagesLoadingMore"
          :has-more="store.messagesHasMore"
          :streaming="store.streaming"
          @hint="onHint"
          @open-meme="onOpenMeme"
        />

        <AiComposer
          ref="composer"
          v-model="draft"
          :streaming="store.streaming"
          @submit="onSubmit"
          @stop="onStop"
        />
      </section>
    </div>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import AiSessionSidebar from '@/components/ai/AiSessionSidebar.vue'
import AiChatThread from '@/components/ai/AiChatThread.vue'
import AiComposer from '@/components/ai/AiComposer.vue'
import { useAiChatStore } from '@/stores/aiChat'
import { isAuthErrorHandled } from '@/utils/authSession'
import { buildMemeDetailLocation } from '@/utils/pageBreadcrumb'

export default {
  name: 'AiSearchPage',
  components: { AiSessionSidebar, AiChatThread, AiComposer },
  data() {
    return {
      sidebarCollapsed: false,
      draft: '',
    }
  },
  computed: {
    store() {
      return useAiChatStore()
    },
  },
  async mounted() {
    try {
      await this.store.loadSessions({ reset: true })
    } catch (e) {
      if (isAuthErrorHandled(e)) return
      ElMessage.error(e.message || '加载会话失败')
    }
    this.$nextTick(() => {
      if (this.$refs.composer) this.$refs.composer.focus()
    })
  },
  beforeUnmount() {
    this.store.abortStream()
  },
  methods: {
    onNewChat() {
      this.store.startNewChat()
      this.draft = ''
      this.$nextTick(() => {
        if (this.$refs.composer) this.$refs.composer.focus()
      })
    },
    async onSelectSession(sessionId) {
      try {
        await this.store.selectSession(sessionId)
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '加载消息失败')
      }
    },
    async onDeleteSession(sessionId) {
      try {
        await ElMessageBox.confirm('删除后无法恢复该对话，确定继续？', '删除会话', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        })
      } catch {
        return
      }
      try {
        await this.store.removeSession(sessionId)
        ElMessage.success('已删除')
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '删除失败')
      }
    },
    async onLoadMoreSessions() {
      try {
        await this.store.loadSessions({ reset: false })
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '加载失败')
      }
    },
    onHint(text) {
      this.draft = text
      this.onSubmit(text)
    },
    onStop() {
      this.store.abortStream()
    },
    async onSubmit(text) {
      const query = text != null ? String(text).trim() : String(this.draft || '').trim()
      if (!query) return
      this.draft = ''
      try {
        await this.store.sendQuery(query)
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '发送失败')
      }
    },
    onOpenMeme(memeId) {
      const id = memeId != null ? String(memeId).trim() : ''
      if (!id) return
      this.$router.push(
        buildMemeDetailLocation(id, {
          from: 'ai',
          fromRoute: this.$route,
        })
      )
    },
  },
}
</script>

<style scoped>
.ai-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 16px 24px;
}

.ai-shell {
  display: flex;
  min-height: calc(100vh - 148px);
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-lg);
  overflow: hidden;
  background:
    radial-gradient(ellipse at 8% 0%, rgba(49, 138, 239, 0.08), transparent 42%),
    var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.ai-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
}

.ai-main__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 22px 10px;
  border-bottom: 1px solid var(--meme-border);
}

.ai-main__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--meme-text);
  max-width: 520px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-main__subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

@media (max-width: 860px) {
  .ai-shell {
    flex-direction: column;
    min-height: calc(100vh - 132px);
  }
}
</style>
