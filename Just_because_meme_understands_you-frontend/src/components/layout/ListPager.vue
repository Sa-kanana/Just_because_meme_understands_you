<template>
  <nav v-if="visible" class="list-pager" :aria-label="ariaLabel">
    <button
      type="button"
      class="list-pager__btn"
      :disabled="loading || page <= 1"
      aria-label="上一页"
      @click="go(page - 1)"
    >
      <i class="ri-arrow-left-s-line" aria-hidden="true" />
      上一页
    </button>

    <div class="list-pager__pages" role="list">
      <button
        v-for="item in pageItems"
        :key="`p-${item.key}`"
        type="button"
        class="list-pager__page"
        :class="{
          'is-active': item.type === 'page' && item.value === page,
          'is-ellipsis': item.type === 'ellipsis',
        }"
        :disabled="loading || item.type !== 'page' || item.value === page"
        :aria-current="item.type === 'page' && item.value === page ? 'page' : undefined"
        @click="item.type === 'page' && go(item.value)"
      >
        {{ item.type === 'ellipsis' ? '…' : item.value }}
      </button>
    </div>

    <button
      type="button"
      class="list-pager__btn"
      :disabled="loading || page >= totalPages"
      aria-label="下一页"
      @click="go(page + 1)"
    >
      下一页
      <i class="ri-arrow-right-s-line" aria-hidden="true" />
    </button>

    <p v-if="showTotal" class="list-pager__meta">
      共 {{ total }} 条 · {{ totalPages }} 页
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
    totalPages() {
      const size = Math.max(1, Number(this.pageSize) || 1)
      const total = Math.max(0, Number(this.total) || 0)
      return Math.max(1, Math.ceil(total / size))
    },
    visible() {
      const total = Number(this.total) || 0
      if (total <= 0) return false
      if (this.totalPages <= 1) return this.showWhenSingle
      return true
    },
    pageItems() {
      const current = Math.min(Math.max(1, Number(this.page) || 1), this.totalPages)
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
      if (page === this.page || this.loading) return
      this.$emit('update:page', page)
      this.$emit('change', page)
    },
  },
}
</script>

<style scoped>
.list-pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 10px;
  padding: 16px 0 4px;
}

.list-pager__btn,
.list-pager__page {
  height: 34px;
  min-width: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
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

.list-pager__page.is-ellipsis {
  min-width: 28px;
  padding: 0 4px;
  border-color: transparent;
  background: transparent;
  cursor: default;
  color: var(--meme-text-muted);
}

.list-pager__btn:disabled,
.list-pager__page:disabled:not(.is-active):not(.is-ellipsis) {
  opacity: 0.45;
  cursor: not-allowed;
}

.list-pager__pages {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.list-pager__meta {
  margin: 0;
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: var(--meme-text-muted);
}

@media (max-width: 640px) {
  .list-pager__btn {
    padding: 0 10px;
  }

  .list-pager__meta {
    order: 3;
  }
}
</style>
