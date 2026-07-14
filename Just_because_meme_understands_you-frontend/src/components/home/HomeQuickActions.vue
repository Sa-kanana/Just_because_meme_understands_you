<template>
  <section class="home-quick-actions" aria-label="快捷入口">
    <button
      v-for="action in actions"
      :key="action.key"
      type="button"
      class="home-quick-actions__item"
      @click="$emit('action', action)"
    >
      <span class="home-quick-actions__icon" aria-hidden="true">
        <component :is="iconFor(action.key)" />
      </span>
      <span class="home-quick-actions__label">{{ action.label }}</span>
    </button>
  </section>
</template>

<script>
import { h } from 'vue'

const ICONS = {
  publish: () => h('svg', { viewBox: '0 0 24 24', fill: 'none' }, [
    h('path', {
      d: 'M12 5v14M5 12h14',
      stroke: 'currentColor',
      'stroke-width': '2',
      'stroke-linecap': 'round',
    }),
  ]),
  search: () => h('svg', { viewBox: '0 0 24 24', fill: 'none' }, [
    h('circle', { cx: '11', cy: '11', r: '6.5', stroke: 'currentColor', 'stroke-width': '1.75' }),
    h('path', {
      d: 'M16 16l4.5 4.5',
      stroke: 'currentColor',
      'stroke-width': '1.75',
      'stroke-linecap': 'round',
    }),
  ]),
  favorites: () => h('svg', { viewBox: '0 0 24 24', fill: 'none' }, [
    h('path', {
      d: 'M12 3.5l2.38 4.82 5.32.77-3.85 3.75.91 5.3L12 15.9l-4.76 2.24.91-5.3-3.85-3.75 5.32-.77L12 3.5Z',
      stroke: 'currentColor',
      'stroke-width': '1.5',
      'stroke-linejoin': 'round',
    }),
  ]),
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
    iconFor(key) {
      return ICONS[key] || ICONS.search
    },
  },
}
</script>

<style scoped>
.home-quick-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.home-quick-actions__item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 42px;
  padding: 8px 12px;
  border: 1px solid var(--meme-border-accent);
  border-radius: 12px;
  background: var(--meme-gradient-card);
  color: var(--meme-text);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.home-quick-actions__item:hover {
  transform: translateY(-1px);
  border-color: var(--meme-primary);
  box-shadow: var(--meme-shadow-card);
}

.home-quick-actions__icon {
  display: inline-flex;
  width: 20px;
  height: 20px;
  color: var(--meme-primary);
}

.home-quick-actions__icon :deep(svg) {
  width: 100%;
  height: 100%;
}

@media (max-width: 640px) {
  .home-quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
