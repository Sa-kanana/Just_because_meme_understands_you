<template>
  <nav v-if="items.length" class="app-breadcrumb" aria-label="面包屑导航">
    <ol class="app-breadcrumb__list">
      <li
        v-for="(item, index) in visibleItems"
        :key="`${index}-${item.label}`"
        class="app-breadcrumb__item"
        :class="{ 'is-current': item.current, 'is-loading': item.loading }"
      >
        <span v-if="index > 0" class="app-breadcrumb__sep" aria-hidden="true">
          <i class="ri-arrow-right-s-line" />
        </span>

        <router-link
          v-if="item.to && !item.current"
          :to="item.to"
          class="app-breadcrumb__link"
          :class="{ 'app-breadcrumb__link--home': item.isHome }"
          :title="item.label"
        >
          <i v-if="item.isHome" class="ri-home-5-line app-breadcrumb__home-icon" aria-hidden="true" />
          <span>{{ formatLabel(item.label) }}</span>
        </router-link>

        <span
          v-else
          class="app-breadcrumb__text"
          :class="{ 'app-breadcrumb__text--current': item.current }"
          :title="item.label"
          :aria-current="item.current ? 'page' : undefined"
        >
          <i
            v-if="item.isHome && item.current"
            class="ri-home-5-line app-breadcrumb__home-icon"
            aria-hidden="true"
          />
          <span v-if="item.loading" class="app-breadcrumb__loading">加载中…</span>
          <template v-else>{{ formatLabel(item.label) }}</template>
          <span
            v-if="item.badge"
            class="app-breadcrumb__badge"
            :class="badgeClass(item.badge)"
          >
            {{ item.badge }}
          </span>
        </span>
      </li>
    </ol>
  </nav>
</template>

<script>
import { truncateBreadcrumbLabel } from '@/utils/pageBreadcrumb'

export default {
  name: 'AppBreadcrumb',
  props: {
    items: {
      type: Array,
      default: () => [],
    },
    maxLabelLength: {
      type: Number,
      default: 28,
    },
    collapseMiddle: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    normalizedItems() {
      return (this.items || []).filter((item) => item && item.label)
    },
    visibleItems() {
      const items = this.normalizedItems
      if (!this.collapseMiddle || items.length <= 4) return items

      const first = items[0]
      const last = items[items.length - 1]
      const prev = items[items.length - 2]
      return [
        first,
        { label: '…', current: false },
        prev,
        last,
      ]
    },
  },
  methods: {
    formatLabel(label) {
      if (label === '…') return label
      return truncateBreadcrumbLabel(label, this.maxLabelLength)
    },
    badgeClass(badge) {
      const text = String(badge || '')
      if (text.includes('审核') || text.includes('预览')) return 'app-breadcrumb__badge--review'
      if (text.includes('下架')) return 'app-breadcrumb__badge--offline'
      return 'app-breadcrumb__badge--default'
    },
  },
}
</script>

<style scoped>
.app-breadcrumb {
  margin: 0 0 16px;
}

.app-breadcrumb__list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.app-breadcrumb__item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
}

.app-breadcrumb__sep {
  display: inline-flex;
  align-items: center;
  margin: 0 2px;
  color: var(--meme-text-muted);
  font-size: 16px;
  line-height: 1;
  user-select: none;
}

.app-breadcrumb__link,
.app-breadcrumb__text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  max-width: 100%;
  font-size: 13px;
  line-height: 1.4;
}

.app-breadcrumb__link {
  color: var(--meme-text-secondary);
  text-decoration: none;
  border-radius: var(--meme-radius-sm);
  padding: 3px 6px;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.app-breadcrumb__link:hover {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.app-breadcrumb__home-icon {
  font-size: 14px;
  line-height: 1;
}

.app-breadcrumb__text {
  color: var(--meme-text-secondary);
  padding: 3px 2px;
}

.app-breadcrumb__text--current {
  color: var(--meme-text);
  font-weight: 600;
}

.app-breadcrumb__loading {
  color: var(--meme-text-muted);
  font-weight: 500;
}

.app-breadcrumb__badge {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.5;
}

.app-breadcrumb__badge--review {
  color: var(--meme-accent-text);
  background: var(--meme-primary-soft);
}

.app-breadcrumb__badge--offline {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
}

.app-breadcrumb__badge--default {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
}

@media (max-width: 640px) {
  .app-breadcrumb__link,
  .app-breadcrumb__text {
    font-size: 12px;
  }
}
</style>
