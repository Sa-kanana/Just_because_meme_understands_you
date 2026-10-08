<template>
  <nav v-if="visible" class="list-pager" :aria-label="ariaLabel" :aria-busy="loading">
    <p v-if="showTotal" class="list-pager__summary">
      显示 <strong>{{ rangeStart }}–{{ rangeEnd }}</strong> 条，共 {{ normalizedTotal }} 条
    </p>

    <div class="list-pager__controls" role="group" aria-label="分页导航">
      <button
        type="button"
        class="list-pager__btn"
        :disabled="loading || safePage <= 1"
        aria-label="上一页"
        @click="go(safePage - 1)"
      >
        <i class="ri-arrow-left-s-line" aria-hidden="true" />
        <span>上一页</span>
      </button>

      <div class="list-pager__pages" role="group" aria-label="选择页码">
        <template v-for="item in pageItems" :key="`p-${item.key}`">
          <span v-if="item.type === 'ellipsis'" class="list-pager__ellipsis" aria-hidden="true">…</span>
          <button
            v-else
            type="button"
            class="list-pager__page"
            :class="{ 'is-active': item.value === safePage }"
            :disabled="loading || item.value === safePage"
            :aria-label="`第 ${item.value} 页`"
            :aria-current="item.value === safePage ? 'page' : undefined"
            @click="go(item.value)"
          >
            {{ item.value }}
          </button>
        </template>
      </div>

      <span class="list-pager__compact-page" aria-live="polite">
        {{ safePage }} <span>/</span> {{ totalPages }}
      </span>

      <button
        type="button"
        class="list-pager__btn"
        :disabled="loading || safePage >= totalPages"
        aria-label="下一页"
        @click="go(safePage + 1)"
      >
        <span>下一页</span>
        <i class="ri-arrow-right-s-line" aria-hidden="true" />
      </button>
    </div>

    <p v-if="showTotal" class="list-pager__position" aria-live="polite">
      第 <strong>{{ safePage }}</strong> / {{ totalPages }} 页
    </p>
  </nav>
</template>

<script>
export default {
  name: 'ListPager',
  props: {
    page: {
      type: Number,
      default: 1,
    },
    pageSize: {
      type: Number,
      default: 10,
    },
    total: {
      type: Number,
      default: 0,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    showTotal: {
      type: Boolean,
      default: true,
    },
    /** 仅一页时是否仍展示（默认不展示） */
    showWhenSingle: {
      type: Boolean,
      default: false,
    },
    ariaLabel: {
      type: String,
      default: '分页',
    },
  },
  emits: ['update:page', 'change'],
  computed: {
    normalizedTotal() {
      return Math.max(0, Number(this.total) || 0)
    },
    totalPages() {
      const size = Math.max(1, Number(this.pageSize) || 1)
      return Math.max(1, Math.ceil(this.normalizedTotal / size))
    },
    safePage() {
      return Math.min(Math.max(1, Number(this.page) || 1), this.totalPages)
    },
    rangeStart() {
      return this.normalizedTotal === 0 ? 0 : (this.safePage - 1) * Math.max(1, Number(this.pageSize) || 1) + 1
    },
    rangeEnd() {
      return Math.min(this.normalizedTotal, this.safePage * Math.max(1, Number(this.pageSize) || 1))
    },
    visible() {
      if (this.normalizedTotal <= 0) return false
      if (this.totalPages <= 1) return this.showWhenSingle
      return true
    },
    pageItems() {
      const current = this.safePage
      const total = this.totalPages
      if (total <= 7) {
        return Array.from({ length: total }, (_, i) => ({
          type: 'page',
          value: i + 1,
          key: `n-${i + 1}`,
        }))
      }
      const pages = new Set([1, total, current, current - 1, current + 1, current - 2, current + 2])
      const sorted = [...pages].filter((n) => n >= 1 && n <= total).sort((a, b) => a - b)
      const items = []
      let prev = 0
      sorted.forEach((n) => {
        if (prev && n - prev > 1) {
          items.push({ type: 'ellipsis', key: `e-${prev}-${n}` })
        }
        items.push({ type: 'page', value: n, key: `n-${n}` })
        prev = n
      })
      return items
    },
  },
  methods: {
    go(next) {
      const page = Math.min(Math.max(1, Number(next) || 1), this.totalPages)
      if (page === this.safePage || this.loading) return
      this.$emit('update:page', page)
      this.$emit('change', page)
    },
  },
}
</script>

<style scoped>
.list-pager {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 12px 20px;
  margin-top: 20px;
  padding: 18px 8px 4px;
  border-top: 1px solid var(--meme-border);
}

.list-pager__summary,
.list-pager__position {
  margin: 0;
  color: var(--meme-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.list-pager__summary strong,
.list-pager__position strong {
  color: var(--meme-text-secondary);
  font-weight: 700;
}

.list-pager__position {
  text-align: right;
}

.list-pager__controls {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.list-pager__btn,
.list-pager__page {
  height: 34px;
  min-width: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-elevated);
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.list-pager__btn:hover:not(:disabled),
.list-pager__page:hover:not(:disabled) {
  color: var(--meme-text);
  border-color: var(--meme-border-strong);
  background: var(--meme-bg-muted);
}

.list-pager__page.is-active {
  color: #fff;
  border-color: transparent;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 4px 12px var(--meme-focus-ring);
  cursor: default;
}

.list-pager__ellipsis {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  color: var(--meme-text-muted);
}

.list-pager__btn:disabled,
.list-pager__page:disabled:not(.is-active) {
  opacity: 0.45;
  cursor: not-allowed;
}

.list-pager__pages {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.list-pager__compact-page {
  display: none;
  min-width: 52px;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  white-space: nowrap;
}

.list-pager__compact-page span {
  color: var(--meme-text-muted);
}

@media (max-width: 640px) {
  .list-pager {
    grid-template-columns: 1fr;
    justify-items: center;
    gap: 12px;
    margin-top: 16px;
    padding: 16px 0 4px;
  }

  .list-pager__summary {
    order: 0;
  }

  .list-pager__controls {
    order: 1;
    width: 100%;
  }

  .list-pager__pages,
  .list-pager__position {
    display: none;
  }

  .list-pager__compact-page {
    display: inline-block;
  }

  .list-pager__btn {
    padding: 0 10px;
  }
}
</style>
