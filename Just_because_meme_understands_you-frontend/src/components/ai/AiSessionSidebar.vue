<template>
  <aside class="ai-sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="ai-sidebar__toolbar">
      <el-button type="primary" class="ai-sidebar__new" @click="$emit('new-chat')">
        <i class="ri-add-line" aria-hidden="true" />
        新对话
      </el-button>
      <button
        type="button"
        class="ai-sidebar__collapse"
        :title="collapsed ? '展开会话' : '收起会话'"
        :aria-label="collapsed ? '展开会话' : '收起会话'"
        @click="$emit('toggle-collapse')"
      >
        <i :class="collapsed ? 'ri-menu-unfold-line' : 'ri-menu-fold-line'" aria-hidden="true" />
      </button>
    </div>

    <div v-if="!collapsed" class="ai-sidebar__body">
      <div v-if="loading && !sessions.length" class="ai-sidebar__loading">
        <el-skeleton :rows="5" animated />
      </div>
      <el-empty
        v-else-if="!sessions.length"
        description="还没有对话"
        :image-size="64"
      />
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
            <span class="ai-session-item__title">{{ session.title || '新对话' }}</span>
            <span v-if="session.updateTime" class="ai-session-item__time">
              {{ formatTime(session.updateTime) }}
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
        <el-button text :loading="loadingMore" @click="$emit('load-more')">
          加载更多
        </el-button>
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
      // 后端多为 yyyy-MM-dd HH:mm:ss
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
  width: 280px;
  min-width: 280px;
  border-right: 1px solid var(--meme-border);
  background: var(--meme-bg-elevated);
  transition: width 0.2s ease, min-width 0.2s ease;
}

.ai-sidebar.is-collapsed {
  width: 64px;
  min-width: 64px;
}

.ai-sidebar__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 12px 10px;
}

.ai-sidebar__new {
  flex: 1;
  min-width: 0;
}

.ai-sidebar.is-collapsed .ai-sidebar__new {
  display: none;
}

.ai-sidebar__collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-sm);
  background: var(--meme-bg-card);
  color: var(--meme-text-secondary);
  cursor: pointer;
}

.ai-sidebar__collapse:hover {
  color: var(--meme-primary);
  border-color: var(--meme-primary);
}

.ai-sidebar__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 8px 12px;
}

.ai-sidebar__loading {
  padding: 8px;
}

.ai-session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-session-item {
  display: flex;
  align-items: stretch;
  border-radius: var(--meme-radius-md);
  overflow: hidden;
}

.ai-session-item.is-active {
  background: var(--meme-primary-soft);
}

.ai-session-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 8px 10px 12px;
  border: none;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.ai-session-item__title {
  width: 100%;
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-session-item__time {
  font-size: 11px;
  color: var(--meme-text-muted);
}

.ai-session-item__delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  border: none;
  background: transparent;
  color: var(--meme-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}

.ai-session-item:hover .ai-session-item__delete,
.ai-session-item.is-active .ai-session-item__delete {
  opacity: 1;
}

.ai-session-item__delete:hover {
  color: var(--meme-danger);
}

.ai-sidebar__more {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}

@media (max-width: 860px) {
  .ai-sidebar {
    width: 100%;
    min-width: 0;
    border-right: none;
    border-bottom: 1px solid var(--meme-border);
    max-height: 220px;
  }

  .ai-sidebar.is-collapsed {
    width: 100%;
    max-height: 56px;
  }
}
</style>
