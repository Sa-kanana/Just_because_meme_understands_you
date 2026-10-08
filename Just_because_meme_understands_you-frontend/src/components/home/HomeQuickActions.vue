<template>
  <section class="home-quick-actions" aria-label="快捷入口">
    <vs-button
      v-for="(action, index) in actions"
      :key="action.key"
      type="flat"
      color="primary"
      class="home-quick-actions__item"
      :style="{ '--delay': `${index * 40}ms` }"
      @click="$emit('action', action)"
    >
      <span class="home-quick-actions__icon" aria-hidden="true">
        <i :class="iconClass(action.key)" />
      </span>
      <span class="home-quick-actions__copy">
        <span class="home-quick-actions__label">{{ action.label }}</span>
        <span class="home-quick-actions__hint">{{ actionHint(action.key) }}</span>
      </span>
      <i class="ri-arrow-right-s-line home-quick-actions__arrow" aria-hidden="true" />
    </vs-button>
  </section>
</template>

<script>
const ICON_MAP = {
  publish: 'ri-add-circle-line',
  search: 'ri-search-2-line',
  following: 'ri-user-heart-line',
  favorites: 'ri-star-line',
  ai: 'ri-sparkling-2-line',
}

const HINT_MAP = {
  publish: '分享你的快乐',
  search: '发现感兴趣的内容',
  following: '看看关注的创作者',
  favorites: '重温收藏的梗图',
  ai: '用 AI 找到更多梗',
}

export default {
  name: 'HomeQuickActions',
  props: {
    actions: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['action'],
  methods: {
    iconClass(key) {
      return ICON_MAP[key] || 'ri-compass-3-line'
    },
    actionHint(key) {
      return HINT_MAP[key] || '查看更多内容'
    },
  },
}
</script>

<style scoped>
.home-quick-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.home-quick-actions__item.vs-button {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  min-height: 68px;
  margin: 0;
  padding: 10px 14px !important;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-lg) !important;
  background: var(--meme-bg-card) !important;
  color: var(--meme-text);
  box-shadow: 0 4px 16px rgba(27, 69, 125, 0.035);
  transition: border-color 0.15s ease, background 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
  animation: home-qa-in 0.3s ease-out both;
  animation-delay: var(--delay, 0ms);
}

.home-quick-actions__item :deep(.vs-button__content) {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
  text-align: left;
}

.home-quick-actions__item.vs-button:hover {
  border-color: var(--meme-border-accent);
  background: var(--meme-bg-elevated);
  box-shadow: var(--meme-shadow-soft);
  transform: translateY(-2px);
}

.home-quick-actions__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 13px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-size: 19px;
  flex-shrink: 0;
}

.home-quick-actions__copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}

.home-quick-actions__label {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--meme-text);
}

.home-quick-actions__hint {
  font-size: 12px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--meme-text-secondary);
}

.home-quick-actions__arrow {
  flex-shrink: 0;
  font-size: 18px;
  color: var(--meme-text-muted);
}

@keyframes home-qa-in {
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
  .home-quick-actions__item {
    animation: none;
  }
}

@media (max-width: 860px) {
  .home-quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .home-quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .home-quick-actions__item.vs-button {
    min-height: 62px;
    padding: 9px !important;
  }

  .home-quick-actions__item :deep(.vs-button__content) {
    gap: 8px;
  }

  .home-quick-actions__icon {
    width: 34px;
    height: 34px;
    font-size: 17px;
  }

  .home-quick-actions__hint,
  .home-quick-actions__arrow {
    display: none;
  }
}
</style>
