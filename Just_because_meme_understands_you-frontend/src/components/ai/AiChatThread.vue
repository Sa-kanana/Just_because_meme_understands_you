<template>
  <div ref="scroller" class="ai-thread">
    <div v-if="loading && !messages.length" class="ai-thread__loading">
      <ui-skeleton :rows="4" animated />
    </div>

    <div v-else-if="!messages.length" class="ai-thread__empty">
      <div class="ai-thread__hero">
        <p class="ai-thread__hero-kicker">快捷灵感</p>
        <h2 class="ai-thread__hero-title">点一下，直接开搜</h2>
        <div class="ai-thread__hints">
          <button
            v-for="(hint, index) in hintItems"
            :key="`${index}-${hint.text}`"
            type="button"
            class="ai-thread__hint"
            :style="{ '--delay': `${index * 45}ms` }"
            :disabled="streaming || hintsLoading"
            @click="$emit('hint', hint.text)"
          >
            <span class="ai-thread__hint-index">{{ index + 1 }}</span>
            <span class="ai-thread__hint-text">{{ hint.text }}</span>
            <i class="ri-corner-down-left-line ai-thread__hint-arrow" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="hasMore" class="ai-thread__earlier">
        <vs-button type="flat" :loading="loadingMore" @click="$emit('load-earlier')">
          加载更早消息
        </vs-button>
      </div>

      <article
        v-for="(msg, index) in messages"
        :key="msg.id"
        class="ai-bubble"
        :class="[
          msg.role === 'user' ? 'is-user' : 'is-assistant',
          { 'is-error': !!msg.error, 'is-streaming': msg.streaming },
        ]"
        :style="{ '--enter-delay': `${Math.min(index, 6) * 40}ms` }"
      >
        <div class="ai-bubble__meta">
          <span class="ai-bubble__avatar" aria-hidden="true">
            <i :class="msg.role === 'user' ? 'ri-user-3-line' : 'ri-robot-2-line'" />
          </span>
          <span>{{ msg.role === 'user' ? '你' : 'AI 搜梗' }}</span>
        </div>

        <div class="ai-bubble__body" :class="{ 'is-waiting': isWaiting(msg) }">
          <div v-if="isWaiting(msg)" class="ai-thinking" aria-live="polite">
            <div class="ai-thinking__bars" aria-hidden="true">
              <span /><span /><span />
            </div>
            <p class="ai-thinking__text">{{ thinkingLabel }}</p>
            <div class="ai-thinking__shimmer" aria-hidden="true">
              <span class="ai-thinking__line ai-thinking__line--lg" />
              <span class="ai-thinking__line ai-thinking__line--md" />
              <span class="ai-thinking__line ai-thinking__line--sm" />
            </div>
          </div>
          <template v-else>
            <p class="ai-bubble__text">{{ displayContent(msg) }}</p>
          </template>
          <p v-if="msg.paused" class="ai-bubble__status" role="status">已停止生成</p>
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
            <span class="ai-cite-chip__title">{{ citeLabel(cite) }}</span>
            <i class="ri-arrow-right-s-line" aria-hidden="true" />
          </button>
        </div>
      </article>
    </template>
  </div>
</template>

<script>
import { sanitizeAiDisplayText } from '@/utils/aiDisplayText'
import { getFallbackHints, loadRandomMemeHints } from '@/utils/aiQuickHints'
import {
  createTypewriterState,
  finishTypewriter,
  markTypewriterComplete,
  revealTypewriter,
  typewriterText,
  updateTypewriterTarget,
} from '@/utils/aiTypewriter.mjs'

const THINKING_LABELS = ['正在检索站内梗库…', '正在整理相关内容…', '正在生成回答…']

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
    return {
      hintItems: getFallbackHints(),
      hintsLoading: false,
      thinkingPhase: 0,
      thinkingTimer: null,
      typewriterStates: Object.create(null),
      typewriterTimer: null,
      typewriterCompletedIds: new Set(),
    }
  },
  computed: {
    isEmptyThread() {
      return !this.messages.length
    },
    thinkingLabel() {
      return THINKING_LABELS[this.thinkingPhase % THINKING_LABELS.length]
    },
  },
  watch: {
    isEmptyThread: {
      immediate: true,
      handler(empty) {
        if (empty) this.refreshHints()
      },
    },
    messages: {
      deep: true,
      immediate: true,
      handler() {
        this.syncTypewriterMessages()
        this.$nextTick(() => this.scrollToBottom())
      },
    },
    streaming: {
      immediate: true,
      handler(val) {
        this.clearThinkingTimer()
        if (val) {
          this.thinkingPhase = 0
          this.thinkingTimer = window.setInterval(() => {
            this.thinkingPhase = (this.thinkingPhase + 1) % THINKING_LABELS.length
          }, 1800)
          this.$nextTick(() => this.scrollToBottom())
        }
      },
    },
  },
  beforeUnmount() {
    this.clearThinkingTimer()
    this.clearTypewriterTimer()
  },
  methods: {
    async refreshHints() {
      if (this.hintsLoading) return
      this.hintsLoading = true
      try {
        this.hintItems = await loadRandomMemeHints(3)
      } finally {
        this.hintsLoading = false
      }
    },
    clearThinkingTimer() {
      if (this.thinkingTimer != null) {
        window.clearInterval(this.thinkingTimer)
        this.thinkingTimer = null
      }
    },
    syncTypewriterMessages() {
      const messageIds = new Set()
      let hasPendingText = false
      const reduceMotion = typeof window !== 'undefined'
        && window.matchMedia
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches

      this.messages.forEach((msg) => {
        if (!msg || msg.role !== 'assistant' || msg.id == null) return
        const id = String(msg.id)
        messageIds.add(id)
        let state = this.typewriterStates[id]
        if (!state && (!msg.streaming && !msg.typewriter)) return
        if (!state && this.typewriterCompletedIds.has(id)) return
        if (!state) {
          state = createTypewriterState(sanitizeAiDisplayText(msg.displayedContent || ''))
          this.typewriterStates[id] = state
        }

        updateTypewriterTarget(state, sanitizeAiDisplayText(msg.content))
        if (reduceMotion) {
          finishTypewriter(state)
          msg.displayedContent = typewriterText(state)
          delete this.typewriterStates[id]
          this.typewriterCompletedIds.add(id)
        } else if (!msg.streaming) {
          markTypewriterComplete(state)
          if (state.visible.length < state.target.length) {
            hasPendingText = true
          } else {
            delete this.typewriterStates[id]
            this.typewriterCompletedIds.add(id)
          }
        } else if (state.visible.length < state.target.length) {
          hasPendingText = true
        }
      })

      Object.keys(this.typewriterStates).forEach((id) => {
        if (!messageIds.has(id)) delete this.typewriterStates[id]
      })
      this.typewriterCompletedIds.forEach((id) => {
        if (!messageIds.has(id)) this.typewriterCompletedIds.delete(id)
      })

      if (hasPendingText) this.startTypewriterTimer()
      else this.clearTypewriterTimer()
    },
    startTypewriterTimer() {
      if (this.typewriterTimer != null) return
      this.typewriterTimer = window.setInterval(() => {
        let hasPendingText = false
        let revealedText = false
        Object.entries(this.typewriterStates).forEach(([id, state]) => {
          const remaining = state.target.length - state.visible.length
          if (remaining <= 0) {
            if (state.finished) {
              delete this.typewriterStates[id]
              this.typewriterCompletedIds.add(id)
            }
            return
          }
          const batchSize = remaining > 48 ? 6 : remaining > 18 ? 3 : 1
          if (revealTypewriter(state, batchSize) > 0) {
            revealedText = true
            const message = this.messages.find((item) => String(item.id) === id)
            if (message) message.displayedContent = typewriterText(state)
          }
          if (state.visible.length < state.target.length) {
            hasPendingText = true
          } else if (state.finished) {
            delete this.typewriterStates[id]
            this.typewriterCompletedIds.add(id)
          }
        })

        if (revealedText) this.$nextTick(() => this.scrollToBottom())
        if (!hasPendingText) this.clearTypewriterTimer()
      }, 28)
    },
    clearTypewriterTimer() {
      if (this.typewriterTimer != null) {
        window.clearInterval(this.typewriterTimer)
        this.typewriterTimer = null
      }
    },
    hasContent(msg) {
      return !!(msg && String(msg.content || '').trim())
    },
    isWaiting(msg) {
      return !!(msg && msg.role === 'assistant' && msg.streaming && !this.hasContent(msg))
    },
    displayContent(msg) {
      if (!msg) return ''
      const raw = msg.content != null ? String(msg.content) : ''
      if (!raw.trim()) return msg.streaming || msg.paused ? '' : '…'
      if (msg.role === 'assistant') {
        const state = msg.id != null ? this.typewriterStates[String(msg.id)] : null
        return state ? typewriterText(state) : sanitizeAiDisplayText(msg.displayedContent || raw)
      }
      return raw
    },
    citeLabel(cite) {
      const title = cite && cite.title != null ? String(cite.title).trim() : ''
      return title || '查看详情'
    },
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
  overscroll-behavior: contain;
  padding: 18px 22px 10px;
  scroll-behavior: smooth;
  background: transparent;
}

.ai-thread__loading {
  max-width: 640px;
  margin: 24px auto;
}

.ai-thread__empty {
  min-height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 8px 8px 4px;
}

.ai-thread__hero {
  max-width: 560px;
  width: 100%;
  padding: 4px 4px 8px;
  animation: ai-hero-in 0.35s ease-out both;
}

.ai-thread__hero-kicker {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  color: var(--meme-text-muted);
}

.ai-thread__hero-title {
  margin: 0 0 14px;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--meme-text);
}

.ai-thread__hints {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

@media (min-width: 720px) {
  .ai-thread__hints {
    grid-template-columns: 1fr 1fr;
  }

  .ai-thread__hint:last-child:nth-child(odd) {
    grid-column: 1 / -1;
  }
}

.ai-thread__hint {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 12%, var(--meme-border));
  border-radius: 14px;
  background: color-mix(in srgb, var(--meme-bg-elevated) 90%, transparent);
  color: var(--meme-text);
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
  animation: ai-hint-in 0.3s ease-out both;
  animation-delay: var(--delay, 0ms);
}

.ai-thread__hint:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--meme-primary) 42%, var(--meme-border));
  background: var(--meme-primary-soft);
  box-shadow: 0 8px 20px var(--meme-focus-ring);
  transform: translateY(-1px);
}

.ai-thread__hint:hover:not(:disabled) .ai-thread__hint-arrow {
  color: var(--meme-primary);
}

.ai-thread__hint:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ai-thread__hint-index {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 750;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.ai-thread__hint:hover:not(:disabled) .ai-thread__hint-index {
  color: #fff;
  background: var(--meme-primary);
}

.ai-thread__hint-text {
  flex: 1;
  min-width: 0;
  font-weight: 650;
  text-align: left;
}

.ai-thread__hint-arrow {
  color: var(--meme-text-muted);
  font-size: 16px;
}

.ai-thread__earlier {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.ai-bubble {
  max-width: 760px;
  margin: 0 auto 18px;
  animation: ai-bubble-in 0.28s ease-out both;
  animation-delay: var(--enter-delay, 0ms);
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--meme-text-muted);
  margin-bottom: 6px;
}

.ai-bubble__avatar {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--meme-radius-sm);
  font-size: 12px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
}

.is-assistant .ai-bubble__avatar {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.is-assistant.is-streaming .ai-bubble__avatar {
  animation: ai-avatar-pulse 1.6s ease-in-out infinite;
}

.ai-bubble__body {
  position: relative;
  max-width: min(100%, 640px);
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.is-user .ai-bubble__body {
  border-bottom-right-radius: 6px;
  background: linear-gradient(145deg, var(--meme-primary) 0%, var(--meme-primary-dark) 100%);
  color: var(--meme-text-inverse);
  box-shadow: 0 8px 18px var(--meme-focus-ring);
}

.is-assistant .ai-bubble__body {
  border-bottom-left-radius: 6px;
  background: color-mix(in srgb, var(--meme-bg-elevated) 92%, transparent);
  border: 1px solid color-mix(in srgb, var(--meme-border) 90%, var(--meme-primary));
  color: var(--meme-text);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.03),
    0 8px 20px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.is-assistant.is-streaming .ai-bubble__body {
  border-color: var(--meme-border-accent);
}

.is-assistant .ai-bubble__body.is-waiting {
  min-width: 220px;
  padding: 14px 16px;
  background:
    linear-gradient(90deg, transparent, rgba(49, 138, 239, 0.04), transparent),
    var(--meme-bg-elevated);
  background-size: 200% 100%, 100% 100%;
  animation: ai-waiting-glow 2.4s ease-in-out infinite;
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

.ai-bubble__status {
  margin: 7px 0 0;
  color: var(--meme-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.ai-thinking {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-thinking__bars {
  display: inline-flex;
  align-items: flex-end;
  gap: 4px;
  height: 14px;
}

.ai-thinking__bars span {
  width: 3px;
  height: 6px;
  border-radius: 1px;
  background: var(--meme-primary);
  animation: ai-bar-wave 1s ease-in-out infinite;
}

.ai-thinking__bars span:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-thinking__bars span:nth-child(3) {
  animation-delay: 0.3s;
}

.ai-thinking__text {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--meme-text-secondary);
  letter-spacing: 0.01em;
}

.ai-thinking__shimmer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-thinking__line {
  display: block;
  height: 8px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    var(--meme-bg-muted) 0%,
    rgba(49, 138, 239, 0.12) 45%,
    var(--meme-bg-muted) 90%
  );
  background-size: 200% 100%;
  animation: ai-shimmer 1.4s ease-in-out infinite;
}

.ai-thinking__line--lg {
  width: 100%;
}

.ai-thinking__line--md {
  width: 78%;
  animation-delay: 0.12s;
}

.ai-thinking__line--sm {
  width: 52%;
  animation-delay: 0.24s;
}

.ai-bubble__cites {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  max-width: min(100%, 640px);
}

.ai-bubble__cites-label {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.ai-cite-chip {
  max-width: 220px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 18%, var(--meme-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--meme-primary-soft) 55%, var(--meme-bg-elevated));
  color: var(--meme-accent-text);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.ai-cite-chip:hover {
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
  box-shadow: 0 4px 12px var(--meme-focus-ring);
  transform: translateY(-1px);
}

.ai-cite-chip__title {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@keyframes ai-avatar-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(49, 138, 239, 0.28);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(49, 138, 239, 0);
  }
}

@keyframes ai-bar-wave {
  0%,
  100% {
    height: 5px;
    opacity: 0.45;
  }
  50% {
    height: 14px;
    opacity: 1;
  }
}

@keyframes ai-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

@keyframes ai-waiting-glow {
  0%,
  100% {
    background-position: 100% 0, 0 0;
  }
  50% {
    background-position: 0% 0, 0 0;
  }
}

@keyframes ai-hero-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes ai-hint-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes ai-bubble-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-thread__hero,
  .ai-thread__hint,
  .ai-bubble,
  .ai-bubble__avatar,
  .ai-thinking__bars span,
  .ai-thinking__line,
  .ai-bubble__body.is-waiting {
    animation: none;
  }
}
</style>
