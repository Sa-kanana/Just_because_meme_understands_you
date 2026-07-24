<template>
  <div ref="scroller" class="ai-thread">
    <div v-if="loading && !messages.length" class="ai-thread__loading">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="!messages.length" class="ai-thread__empty">
      <div class="ai-thread__hero">
        <h2 class="ai-thread__hero-title">AI 搜梗</h2>
        <p class="ai-thread__hero-desc">
          描述场景、情绪或台词，我会检索站内梗图并给出推荐理由。
        </p>
        <div class="ai-thread__hints">
          <button
            v-for="hint in hints"
            :key="hint"
            type="button"
            class="ai-thread__hint"
            :disabled="streaming"
            @click="$emit('hint', hint)"
          >
            {{ hint }}
          </button>
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="hasMore" class="ai-thread__earlier">
        <el-button text :loading="loadingMore" @click="$emit('load-earlier')">
          加载更早消息
        </el-button>
      </div>

      <article
        v-for="msg in messages"
        :key="msg.id"
        class="ai-bubble"
        :class="[
          msg.role === 'user' ? 'is-user' : 'is-assistant',
          { 'is-error': !!msg.error },
        ]"
      >
        <div class="ai-bubble__meta">
          {{ msg.role === 'user' ? '你' : 'AI' }}
        </div>
        <div class="ai-bubble__body">
          <p class="ai-bubble__text">{{ msg.content || (msg.streaming ? '' : '…') }}</p>
          <span v-if="msg.streaming" class="ai-bubble__cursor" aria-hidden="true" />
        </div>

        <div v-if="msg.cites && msg.cites.length" class="ai-bubble__cites">
          <span class="ai-bubble__cites-label">相关梗</span>
          <button
            v-for="cite in msg.cites"
            :key="cite.memeId"
            type="button"
            class="ai-cite-chip"
            @click="$emit('open-meme', cite.memeId)"
          >
            <span class="ai-cite-chip__title">{{ cite.title || `梗 #${cite.memeId}` }}</span>
          </button>
        </div>
      </article>
    </template>
  </div>
</template>

<script>
const DEFAULT_HINTS = [
  '上班摸鱼被发现怎么回',
  '考试周的自我安慰',
  '朋友说下次一定',
]

export default {
  name: 'AiChatThread',
  props: {
    messages: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    loadingMore: { type: Boolean, default: false },
    hasMore: { type: Boolean, default: false },
    streaming: { type: Boolean, default: false },
  },
  emits: ['hint', 'load-earlier', 'open-meme'],
  data() {
    return { hints: DEFAULT_HINTS }
  },
  watch: {
    messages: {
      deep: true,
      handler() {
        this.$nextTick(() => this.scrollToBottom())
      },
    },
    streaming(val) {
      if (val) this.$nextTick(() => this.scrollToBottom())
    },
  },
  methods: {
    scrollToBottom() {
      const el = this.$refs.scroller
      if (!el) return
      el.scrollTop = el.scrollHeight
    },
  },
}
</script>

<style scoped>
.ai-thread {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 22px 12px;
}

.ai-thread__loading {
  max-width: 640px;
  margin: 24px auto;
}

.ai-thread__empty {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 8px;
}

.ai-thread__hero {
  max-width: 520px;
  text-align: center;
}

.ai-thread__hero-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--meme-text);
}

.ai-thread__hero-desc {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.ai-thread__hints {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.ai-thread__hint {
  padding: 8px 12px;
  border: 1px solid var(--meme-border-accent);
  border-radius: 999px;
  background: var(--meme-gradient-card);
  color: var(--meme-text);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.ai-thread__hint:hover:not(:disabled) {
  border-color: var(--meme-primary);
  transform: translateY(-1px);
}

.ai-thread__hint:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ai-thread__earlier {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.ai-bubble {
  max-width: 720px;
  margin: 0 auto 16px;
}

.ai-bubble.is-user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.ai-bubble.is-assistant {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.ai-bubble__meta {
  font-size: 12px;
  color: var(--meme-text-muted);
  margin-bottom: 6px;
}

.ai-bubble__body {
  position: relative;
  max-width: min(100%, 560px);
  padding: 12px 14px;
  border-radius: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.is-user .ai-bubble__body {
  background: var(--meme-primary);
  color: var(--meme-text-inverse);
  border-bottom-right-radius: 4px;
}

.is-assistant .ai-bubble__body {
  background: var(--meme-bg-card);
  border: 1px solid var(--meme-border);
  color: var(--meme-text);
  border-bottom-left-radius: 4px;
  box-shadow: var(--meme-shadow-soft);
}

.is-error .ai-bubble__body {
  border-color: var(--meme-danger);
  background: var(--meme-danger-soft);
  color: var(--meme-text);
}

.ai-bubble__text {
  margin: 0;
  display: inline;
  font-size: 14px;
}

.ai-bubble__cursor {
  display: inline-block;
  width: 7px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--meme-primary);
  animation: ai-cursor-blink 1s steps(1) infinite;
}

@keyframes ai-cursor-blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}

.ai-bubble__cites {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  max-width: min(100%, 560px);
}

.ai-bubble__cites-label {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.ai-cite-chip {
  max-width: 200px;
  padding: 4px 10px;
  border: 1px solid var(--meme-border-accent);
  border-radius: 999px;
  background: var(--meme-bg-muted);
  color: var(--meme-accent-text);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.ai-cite-chip:hover {
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.ai-cite-chip__title {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
