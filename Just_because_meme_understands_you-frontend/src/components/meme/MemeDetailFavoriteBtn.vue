<template>
  <button
    type="button"
    class="meme-favorite-btn"
    :class="[buttonClass, { 'is-compact': compact }]"
    :disabled="disabled || loading"
    :aria-pressed="active"
    @click="$emit('click')"
  >
    <span class="meme-favorite-btn__icon-wrap" aria-hidden="true">
      <svg
        v-if="loading"
        class="meme-favorite-btn__spinner"
        viewBox="0 0 24 24"
        fill="none"
      >
        <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="14 42" />
      </svg>
      <svg
        v-else
        class="meme-favorite-btn__icon"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M7 3.5A2.5 2.5 0 0 0 4.5 6v13.8L12 16.2l7.5 3.6V6A2.5 2.5 0 0 0 17 3.5H7Z"
          :fill="active ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linejoin="round"
        />
      </svg>
    </span>
    <span class="meme-favorite-btn__content">
      <span class="meme-favorite-btn__label">{{ label }}</span>
      <span v-if="hint && !preview && !compact" class="meme-favorite-btn__hint">{{ hint }}</span>
    </span>
  </button>
</template>

<script>
export default {
  name: 'MemeDetailFavoriteBtn',
  props: {
    active: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    preview: {
      type: Boolean,
      default: false,
    },
    compact: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
  computed: {
    label() {
      if (this.preview) return '预览中'
      if (this.loading) return this.active ? '取消中…' : '收藏中…'
      return this.active ? '已收藏' : '收藏'
    },
    hint() {
      if (this.preview || this.loading) return ''
      return this.active ? '已加入收藏夹' : '加入我的收藏'
    },
    buttonClass() {
      return {
        'is-active': this.active && !this.preview,
        'is-loading': this.loading,
        'is-preview': this.preview,
        'is-disabled': this.disabled,
      }
    },
  },
}
</script>

<style scoped>
.meme-favorite-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 18px 0 14px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 32%, var(--meme-border));
  background: color-mix(in srgb, var(--meme-primary) 10%, var(--meme-bg-elevated));
  color: var(--meme-primary);
  cursor: pointer;
  box-shadow: none;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease,
    filter 0.15s ease;
}

.meme-favorite-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--meme-primary) 55%, var(--meme-border));
  background: color-mix(in srgb, var(--meme-primary) 16%, var(--meme-bg-elevated));
}

.meme-favorite-btn:active:not(:disabled) {
  filter: brightness(0.97);
}

.meme-favorite-btn.is-active {
  border-color: color-mix(in srgb, var(--meme-primary) 48%, var(--meme-border));
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.meme-favorite-btn.is-active:hover:not(:disabled) {
  filter: brightness(1.05);
}

.meme-favorite-btn.is-preview,
.meme-favorite-btn.is-disabled {
  border-color: var(--meme-border);
  background: var(--meme-bg-muted);
  color: var(--meme-text-muted);
  box-shadow: none;
  cursor: not-allowed;
}

.meme-favorite-btn.is-loading {
  cursor: wait;
}

.meme-favorite-btn__icon-wrap {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.meme-favorite-btn__icon {
  width: 22px;
  height: 22px;
}

.meme-favorite-btn__spinner {
  width: 20px;
  height: 20px;
  animation: meme-favorite-spin 0.75s linear infinite;
}

@keyframes meme-favorite-spin {
  to {
    transform: rotate(360deg);
  }
}

.meme-favorite-btn__content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  text-align: left;
}

.meme-favorite-btn__label {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.meme-favorite-btn__hint {
  margin-top: 1px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
  opacity: 0.78;
}

.meme-favorite-btn.is-compact .meme-favorite-btn__content {
  flex-direction: row;
  align-items: center;
}

.meme-favorite-btn.is-compact {
  min-height: 38px;
  padding: 0 14px 0 12px;
  gap: 8px;
}

.meme-favorite-btn.is-compact .meme-favorite-btn__label {
  font-size: 13px;
}

.meme-favorite-btn.is-compact .meme-favorite-btn__icon-wrap,
.meme-favorite-btn.is-compact .meme-favorite-btn__icon {
  width: 18px;
  height: 18px;
}

@media (max-width: 767px) {
  .meme-favorite-btn.is-compact {
    width: 100%;
    justify-content: center;
  }
}

@media (max-width: 767px) {
  .meme-favorite-btn {
    width: 100%;
    justify-content: center;
    padding: 0 16px;
  }

  .meme-favorite-btn__content {
    align-items: center;
    text-align: center;
  }
}
</style>
