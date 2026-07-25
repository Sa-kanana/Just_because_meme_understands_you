<template>
  <div class="ai-page">
    <div class="ai-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
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
          <div class="ai-main__brand">
            <span class="ai-main__mark" aria-hidden="true">
              <i class="ri-sparkling-2-fill" />
            </span>
            <div class="ai-main__titles">
              <p class="ai-main__brand-label">AI 搜梗</p>
              <h1 class="ai-main__title">
                {{ store.currentSession?.title || '新对话' }}
              </h1>
              <p v-if="!store.messages.length && !store.messagesLoading" class="ai-main__subtitle">
                描述场景、情绪或台词，检索站内梗图
              </p>
            </div>
          </div>
        </header>

        <AiChatThread
          :messages="store.messages"
          :loading="store.messagesLoading"
          :loading-more="store.messagesLoadingMore"
          :has-more="store.messagesHasMore"
          :streaming="store.streaming"
          @hint="onHint"
          @load-earlier="onLoadEarlier"
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
import { toast, confirmBox } from '@/utils/uiFeedback'
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
      toast.error(e.message || '加载会话失败')
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
        toast.error(e.message || '加载消息失败')
      }
    },
    async onDeleteSession(sessionId) {
      try {
        await confirmBox('确定删除该对话？删除后不可恢复。', '删除对话', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        })
      } catch (e) {
        if (e === 'cancel') return
        return
      }
      try {
        await this.store.removeSession(sessionId)
        toast.success('已删除')
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '删除失败')
      }
    },
    async onLoadMoreSessions() {
      try {
        await this.store.loadSessions({ reset: false })
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '加载失败')
      }
    },
    async onLoadEarlier() {
      try {
        await this.store.loadMessages({ reset: false })
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '加载失败')
      }
    },
    async onHint(text) {
      const query = text != null ? String(text).trim() : ''
      if (!query || this.store.streaming) return
      this.draft = ''
      try {
        await this.store.sendQuery(query)
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '发送失败')
      }
    },
    onStop() {
      this.store.abortStream()
    },
    async onSubmit(query) {
      this.draft = ''
      try {
        await this.store.sendQuery(query)
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        this.draft = query
        toast.error(e.message || '发送失败')
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
  position: relative;
  display: flex;
  flex-direction: column;
  max-width: 1180px;
  height: calc(100dvh - 124px);
  max-height: calc(100dvh - 124px);
  margin: 0 auto;
  overflow: hidden;
}

.ai-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  height: 100%;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 12%, var(--meme-border));
  border-radius: 20px;
  overflow: hidden;
  background: var(--meme-bg-card);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.03),
    0 18px 48px rgba(15, 23, 42, 0.07);
}

.ai-main {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(820px 380px at 78% -8%, color-mix(in srgb, var(--meme-primary) 16%, transparent), transparent 58%),
    radial-gradient(640px 320px at 8% 108%, color-mix(in srgb, var(--meme-primary) 9%, transparent), transparent 52%),
    linear-gradient(180deg, var(--meme-bg-muted), color-mix(in srgb, var(--meme-bg-card) 55%, var(--meme-bg-muted)));
}

.ai-main__head {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid color-mix(in srgb, var(--meme-border) 75%, transparent);
  background: color-mix(in srgb, var(--meme-bg-elevated) 78%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.ai-main__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.ai-main__mark {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  color: #fff;
  font-size: 18px;
  background: linear-gradient(145deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 6px 16px var(--meme-focus-ring);
}

.ai-main__brand-label {
  margin: 0 0 1px;
  font-size: 12px;
  font-weight: 650;
  color: var(--meme-text-muted);
}

.ai-main__title {
  margin: 0;
  font-size: 16px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
  max-width: min(52vw, 480px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-main__subtitle {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--meme-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: min(52vw, 420px);
}

@media (max-width: 860px) {
  .ai-page {
    height: calc(100dvh - 118px);
    max-height: calc(100dvh - 118px);
  }

  .ai-shell {
    flex-direction: column;
    border-radius: 14px;
  }

  .ai-main__head {
    flex-wrap: wrap;
    padding: 12px 14px;
  }
}
</style>
