<template>
  <aside class="ai-sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="ai-sidebar__toolbar">
      <button
        v-if="!collapsed"
        type="button"
        class="ai-sidebar__new"
        @click="$emit('new-chat')"
      >
        <i class="ri-add-line" aria-hidden="true" />
        <span>新对话</span>
      </button>
      <button
        type="button"
        class="ai-sidebar__collapse"
        :title="collapsed ? '展开会话' : '收起会话'"
        :aria-label="collapsed ? '展开会话' : '收起会话'"
        @click="$emit('toggle-collapse')"
      >
        <i :class="collapsed ? 'ri-side-bar-line' : 'ri-side-bar-fill'" aria-hidden="true" />
      </button>
    </div>

    <div v-if="!collapsed" class="ai-sidebar__body">
      <div class="ai-sidebar__section-row">
        <p class="ai-sidebar__section">历史会话</p>
        <span v-if="sessions.length" class="ai-sidebar__count">{{ sessions.length }}</span>
      </div>

      <div v-if="loading && !sessions.length" class="ai-sidebar__loading">
        <ui-skeleton :rows="5" animated />
      </div>

      <div v-else-if="!sessions.length" class="ai-sidebar__empty">
        <span class="ai-sidebar__empty-mark" aria-hidden="true">
          <i class="ri-chat-new-line" />
        </span>
        <p class="ai-sidebar__empty-title">还没有对话</p>
        <p class="ai-sidebar__empty-desc">点上方「新对话」开始搜梗</p>
      </div>

      <ul v-else class="ai-session-list" role="list">
        <li
          v-for="session in sessions"
          :key="session.id"
          class="ai-session-item"
          :class="{ 'is-active': session.id === currentSessionId }"
        >
          <button
            type="button"
            class="ai-session-item__main"
            @click="$emit('select', session.id)"
          >
            <span class="ai-session-item__icon" aria-hidden="true">
              <i class="ri-message-3-line" />
            </span>
            <span class="ai-session-item__copy">
              <span class="ai-session-item__title">{{ session.title || '新对话' }}</span>
              <span v-if="session.updateTime" class="ai-session-item__time">
                {{ formatTime(session.updateTime) }}
              </span>
            </span>
          </button>
          <button
            type="button"
            class="ai-session-item__delete"
            title="删除会话"
            aria-label="删除会话"
            @click.stop="$emit('delete', session.id)"
          >
            <i class="ri-delete-bin-6-line" aria-hidden="true" />
          </button>
        </li>
      </ul>

      <div v-if="hasMore" class="ai-sidebar__more">
        <vs-button
          class="meme-load-more-btn"
          type="border"
          color="primary"
          :loading="loadingMore"
          @click="$emit('load-more')"
        >
          <i v-if="!loadingMore" class="ri-arrow-down-s-line" aria-hidden="true" />
          加载更多
        </vs-button>
      </div>
    </div>
  </aside>
</template>

<script>
export default {
  name: 'AiSessionSidebar',
  props: {
    sessions: { type: Array, default: () => [] },
    currentSessionId: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    loadingMore: { type: Boolean, default: false },
    hasMore: { type: Boolean, default: false },
    collapsed: { type: Boolean, default: false },
  },
  emits: ['new-chat', 'select', 'delete', 'load-more', 'toggle-collapse'],
  methods: {
    formatTime(raw) {
      const text = String(raw || '').trim()
      if (!text) return ''
      const datePart = text.slice(0, 10)
      const timePart = text.length >= 16 ? text.slice(11, 16) : ''
      const today = new Date()
      const y = today.getFullYear()
      const m = String(today.getMonth() + 1).padStart(2, '0')
      const d = String(today.getDate()).padStart(2, '0')
      const todayStr = `${y}-${m}-${d}`
      if (datePart === todayStr && timePart) return timePart
      return datePart
    },
  },
}
</script>

<style scoped>
.ai-sidebar {
  display: flex;
  flex-direction: column;
  width: 268px;
  min-width: 268px;
  min-height: 0;
  height: 100%;
  border-right: 1px solid var(--meme-border);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--meme-primary) 5%, var(--meme-bg-elevated)) 0%, var(--meme-bg-muted) 28%);
  transition: width 0.2s ease, min-width 0.2s ease;
}

.ai-sidebar.is-collapsed {
  width: 58px;
  min-width: 58px;
}

.ai-sidebar__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 12px 12px;
  flex-shrink: 0;
}

.ai-sidebar__new {
  flex: 1;
  min-width: 0;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 14px;
  border: none;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
  color: #fff !important;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 8px 18px var(--meme-focus-ring);
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease, box-shadow 0.15s ease;
}

.ai-sidebar__new:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
  box-shadow: 0 10px 22px var(--meme-focus-ring);
}

.ai-sidebar__new:active {
  transform: translateY(0);
}

.ai-sidebar__new i {
  font-size: 16px;
}

.ai-sidebar__collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--meme-border);
  border-radius: 12px;
  background: var(--meme-bg-elevated);
  color: var(--meme-text-secondary);
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.ai-sidebar__collapse:hover {
  color: var(--meme-primary);
  border-color: color-mix(in srgb, var(--meme-primary) 40%, var(--meme-border));
  background: var(--meme-primary-soft);
}

.ai-sidebar.is-collapsed .ai-sidebar__toolbar {
  justify-content: center;
  padding: 14px 8px;
}

.ai-sidebar__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 10px 14px;
}

.ai-sidebar__section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 4px 6px 10px;
}

.ai-sidebar__section {
  margin: 0;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--meme-text-muted);
}

.ai-sidebar__count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.ai-sidebar__loading {
  padding: 8px;
}

.ai-sidebar__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 36px 12px;
}

.ai-sidebar__empty-mark {
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  border-radius: 14px;
  font-size: 22px;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.ai-sidebar__empty-title {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 700;
  color: var(--meme-text);
}

.ai-sidebar__empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.ai-session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.ai-session-item {
  position: relative;
  display: flex;
  align-items: stretch;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid transparent;
  background: transparent;
  transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.ai-session-item:hover {
  background: color-mix(in srgb, var(--meme-bg-elevated) 88%, transparent);
  border-color: var(--meme-border);
}

.ai-session-item.is-active {
  background: var(--meme-bg-elevated);
  border-color: color-mix(in srgb, var(--meme-primary) 32%, var(--meme-border));
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.ai-session-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 11px;
  bottom: 11px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: var(--meme-primary);
}

.ai-session-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 4px 10px 11px;
  border: none;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.ai-session-item__icon {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  font-size: 14px;
  color: var(--meme-text-muted);
  background: color-mix(in srgb, var(--meme-bg-muted) 80%, var(--meme-bg-elevated));
}

.ai-session-item.is-active .ai-session-item__icon {
  color: #fff;
  background: linear-gradient(145deg, var(--meme-primary), var(--meme-primary-dark));
}

.ai-session-item__copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-session-item__title {
  width: 100%;
  font-size: 13px;
  font-weight: 650;
  color: var(--meme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-session-item.is-active .ai-session-item__title {
  color: var(--meme-primary);
}

.ai-session-item__time {
  font-size: 11px;
  color: var(--meme-text-muted);
}

.ai-session-item__delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  border: none;
  background: transparent;
  color: var(--meme-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: color 0.15s ease, opacity 0.15s ease;
}

.ai-session-item:hover .ai-session-item__delete,
.ai-session-item.is-active .ai-session-item__delete {
  opacity: 1;
}

.ai-session-item__delete:hover {
  color: var(--meme-danger, #e11d48);
}

.ai-sidebar__more {
  display: flex;
  justify-content: center;
  padding: 10px 0 4px;
}

@media (max-width: 860px) {
  .ai-sidebar {
    width: 100%;
    min-width: 0;
    border-right: none;
    border-bottom: 1px solid var(--meme-border);
    max-height: 210px;
  }

  .ai-sidebar.is-collapsed {
    width: 100%;
    max-height: 60px;
  }
}
</style>
