<template>
  <div ref="root" class="tag-picker" @focusout="onFocusOut">
    <button
      ref="trigger"
      type="button"
      class="tag-picker__trigger"
      :class="{ 'is-open': open, 'has-value': modelValue }"
      aria-haspopup="dialog"
      :aria-expanded="open"
      aria-label="按标签筛选"
      @click="toggle"
    >
      <span class="tag-picker__hash" aria-hidden="true">#</span>
      <span class="tag-picker__value">{{ selectedName }}</span>
      <svg class="tag-picker__chevron" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="m6 9 6 6 6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div v-if="open" class="tag-picker__panel" role="dialog" aria-label="选择标签">
      <div class="tag-picker__search">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" stroke-width="1.7" />
          <path d="m16 16 4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
        </svg>
        <input ref="search" v-model="query" type="search" placeholder="搜索标签" aria-label="搜索标签" />
      </div>
      <button type="button" class="tag-picker__all" :class="{ 'is-selected': !modelValue }" :aria-pressed="!modelValue" @click="select('')">
        全部标签
        <span v-if="!modelValue" aria-hidden="true">✓</span>
      </button>
      <div class="tag-picker__options" @keydown="moveFocus">
        <button
          v-for="tag in filteredTags"
          :key="tag.id"
          type="button"
          class="tag-picker__option"
          :class="{ 'is-selected': String(tag.id) === String(modelValue) }"
          :aria-pressed="String(tag.id) === String(modelValue)"
          @click="select(String(tag.id))"
        >
          <span>{{ tag.name }}</span>
          <span v-if="String(tag.id) === String(modelValue)" class="tag-picker__check" aria-hidden="true">✓</span>
        </button>
        <p v-if="!filteredTags.length" class="tag-picker__empty">{{ tags.length ? '没有匹配的标签' : '暂无可用标签' }}</p>
      </div>
    </div>
  </div>
</template>

<script>
/** 标签筛选面板：按 ID 选择标签，展示和搜索仅使用标签名称。 */
export default {
  name: 'TagFilter',
  props: {
    // 当前选择的标签 ID，空字符串表示不限定标签。
    modelValue: { type: String, default: '' },
    // 可选标签列表；分类信息不参与展示。
    tags: { type: Array, default: () => [] },
  },
  emits: ['update:modelValue', 'change'],
  data() {
    return {
      open: false, // 标签面板是否展开。
      query: '', // 面板内的标签名称搜索词。
    }
  },
  computed: {
    selectedName() {
      return this.tags.find(tag => String(tag.id) === String(this.modelValue))?.name || '全部标签'
    },
    filteredTags() {
      const keyword = this.query.trim().toLocaleLowerCase()
      return this.tags.filter(tag => String(tag.name || '').toLocaleLowerCase().includes(keyword))
    },
  },
  mounted() {
    document.addEventListener('pointerdown', this.onOutsideClick)
    document.addEventListener('keydown', this.onEscape)
  },
  beforeUnmount() {
    document.removeEventListener('pointerdown', this.onOutsideClick)
    document.removeEventListener('keydown', this.onEscape)
  },
  methods: {
    toggle() {
      this.open = !this.open
      if (this.open) {
        this.query = ''
        this.$nextTick(() => this.$refs.search?.focus())
      }
    },
    select(value) {
      this.$emit('update:modelValue', value)
      this.$emit('change', value)
      this.open = false
      this.$refs.trigger?.focus()
    },
    onOutsideClick(event) {
      if (!this.$refs.root?.contains(event.target)) this.open = false
    },
    onFocusOut(event) {
      if (event.relatedTarget && !this.$refs.root?.contains(event.relatedTarget)) this.open = false
    },
    onEscape(event) {
      if (event.key === 'Escape' && this.open) {
        this.open = false
        this.$refs.trigger?.focus()
      }
    },
    moveFocus(event) {
      if (!['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return
      const buttons = Array.from(event.currentTarget.querySelectorAll('button'))
      const current = buttons.indexOf(event.target)
      if (current < 0) return
      event.preventDefault()
      const step = ['ArrowDown', 'ArrowRight'].includes(event.key) ? 1 : -1
      buttons[(current + step + buttons.length) % buttons.length]?.focus()
    },
  },
}
</script>

<style scoped>
.tag-picker { position: relative; width: 190px; max-width: 100%; }
.tag-picker__trigger { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 42px; padding: 8px 14px; border: 1px solid var(--meme-border-accent); border-radius: 12px; background: var(--meme-bg-card); color: var(--meme-text); font: inherit; font-size: 14px; cursor: pointer; transition: border-color .2s, box-shadow .2s; }
.tag-picker__trigger:hover, .tag-picker__trigger.is-open { border-color: var(--meme-primary); box-shadow: 0 0 0 3px var(--meme-primary-soft); }
.tag-picker__hash { color: var(--meme-primary); font-size: 20px; font-weight: 600; }
.tag-picker__value { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: left; }
.tag-picker__trigger.has-value .tag-picker__value { color: var(--meme-primary); font-weight: 600; }
.tag-picker__chevron { width: 16px; height: 16px; flex-shrink: 0; color: var(--meme-text-secondary); transition: transform .2s; }
.is-open .tag-picker__chevron { transform: rotate(180deg); }
.tag-picker__panel { position: absolute; top: calc(100% + 8px); left: 0; z-index: 100; box-sizing: border-box; width: min(320px, calc(100vw - 48px)); padding: 12px; border: 1px solid var(--meme-border); border-radius: 16px; background: var(--meme-bg-elevated, var(--meme-bg-card)); box-shadow: var(--meme-shadow-soft, 0 12px 32px #182b4d1f); }
.tag-picker__search { display: flex; align-items: center; gap: 8px; padding: 9px 11px; border: 1px solid transparent; border-radius: 10px; background: var(--meme-bg-muted); color: var(--meme-text-secondary); }
.tag-picker__search:focus-within { border-color: var(--meme-border-accent); }
.tag-picker__search svg { width: 17px; height: 17px; flex-shrink: 0; }
.tag-picker__search input { min-width: 0; width: 100%; border: none; outline: none; background: transparent; color: var(--meme-text); font: inherit; font-size: 13px; }
.tag-picker__search input::placeholder { color: var(--meme-text-secondary); }
.tag-picker__all { display: flex; justify-content: space-between; width: 100%; margin: 10px 0; padding: 9px 12px; border: none; border-radius: 10px; background: transparent; color: var(--meme-text-secondary); font: inherit; font-size: 13px; cursor: pointer; }
.tag-picker__options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; max-height: min(260px, 45vh); overflow-y: auto; padding: 2px 4px 4px 2px; scrollbar-width: thin; scrollbar-color: var(--meme-border-accent) transparent; }
.tag-picker__option { display: flex; align-items: center; justify-content: space-between; gap: 6px; min-height: 36px; padding: 8px 10px; border: 1px solid var(--meme-border); border-radius: 9px; background: transparent; color: var(--meme-text); font: inherit; font-size: 13px; text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.tag-picker__option:hover, .tag-picker__all:hover { background: var(--meme-bg-muted); }
.tag-picker .is-selected { border-color: var(--meme-border-accent); background: var(--meme-primary-soft); color: var(--meme-primary); font-weight: 600; }
.tag-picker button:focus-visible { outline: 2px solid var(--meme-primary); outline-offset: 2px; }
.tag-picker__check { flex-shrink: 0; }
.tag-picker__empty { grid-column: 1 / -1; margin: 0; padding: 20px 8px; text-align: center; color: var(--meme-text-secondary); font-size: 13px; }
@media (max-width: 520px) { .tag-picker { width: 100%; } .tag-picker__panel { width: 100%; } }
</style>
