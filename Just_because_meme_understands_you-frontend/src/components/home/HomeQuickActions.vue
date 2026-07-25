<template>
  <section class="home-quick-actions" aria-label="快捷入口">
    <button
      v-for="(action, index) in actions"
      :key="action.key"
      type="button"
      class="home-quick-actions__item"
      :style="{ '--delay': `${index * 40}ms` }"
      @click="$emit('action', action)"
    >
      <span class="home-quick-actions__icon" aria-hidden="true">
        <i :class="iconClass(action.key)" />
      </span>
      <span class="home-quick-actions__label">{{ action.label }}</span>
    </button>
  </section>
</template>

<script>
const ICON_MAP = {
  publish: 'ri-add-circle-line',
  search: 'ri-search-2-line',
  favorites: 'ri-star-line',
  ai: 'ri-sparkling-2-line',
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
  },
}
</script>

<style scoped>
.home-quick-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 10px;
}

.home-quick-actions__item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 4px 10px;
  border: 1px solid var(--meme-border);
  border-radius: 10px;
  background: color-mix(in srgb, var(--meme-bg-elevated) 82%, transparent);
  backdrop-filter: blur(8px);
  color: var(--meme-text);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
  animation: home-qa-in 0.3s ease-out both;
  animation-delay: var(--delay, 0ms);
}

.home-quick-actions__item:hover {
  border-color: var(--meme-border-accent);
  background: var(--meme-bg-elevated);
  box-shadow: var(--meme-shadow-soft);
  transform: translateY(-1px);
}

.home-quick-actions__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  color: #fff;
  font-size: 13px;
  flex-shrink: 0;
  box-shadow: 0 3px 8px var(--meme-focus-ring);
}

.home-quick-actions__label {
  flex: 1;
  min-width: 0;
  text-align: left;
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
    grid-template-columns: 1fr;
  }
}
</style>
