<template>
  <button
    type="button"
    class="meme-like-btn"
    :class="[buttonClass, { 'is-compact': compact }]"
    :disabled="disabled || loading"
    :aria-pressed="active"
    @click="$emit('click')"
  >
    <span class="meme-like-btn__icon-wrap" aria-hidden="true">
      <svg
        v-if="loading"
        class="meme-like-btn__spinner"
        viewBox="0 0 24 24"
        fill="none"
      >
        <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="14 42" />
      </svg>
      <svg
        v-else
        class="meme-like-btn__icon"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M7 10.5V20M7 10.5 10.5 4.5a1.5 1.5 0 0 1 2.6-.9L14 10.5h4.5a2 2 0 0 1 1.98 2.35l-1.2 6A2 2 0 0 1 17.32 20H7"
          :fill="active ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </span>
    <span class="meme-like-btn__content">
      <span class="meme-like-btn__label">{{ label }}</span>
      <span v-if="hint && !preview && !compact" class="meme-like-btn__hint">{{ hint }}</span>
    </span>
  </button>
</template>

<script>
export default {
  name: 'MemeDetailLikeBtn',
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
      if (this.loading) return this.active ? '取消中…' : '点赞中…'
      return this.active ? '已点赞' : '点赞'
    },
    hint() {
      if (this.preview || this.loading) return ''
      return this.active ? '已表达支持' : '给这条梗点个赞'
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
.meme-like-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 18px 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(239, 68, 68, 0.28);
  background: linear-gradient(180deg, #ffffff 0%, #fff8f8 100%);
  color: #ef4444;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 6px 18px rgba(239, 68, 68, 0.1);
}

.meme-like-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(239, 68, 68, 0.45);
  box-shadow:
    0 2px 6px rgba(15, 23, 42, 0.06),
    0 10px 24px rgba(239, 68, 68, 0.16);
}

.meme-like-btn:active:not(:disabled) {
  transform: translateY(0);
}

.meme-like-btn.is-active {
  border-color: rgba(239, 68, 68, 0.5);
  background: linear-gradient(135deg, #fff1f2 0%, #ffe4e6 52%, #fecdd3 100%);
  color: #be123c;
  box-shadow:
    0 1px 2px rgba(190, 18, 60, 0.08),
    0 8px 22px rgba(239, 68, 68, 0.22);
}

.meme-like-btn.is-active:hover:not(:disabled) {
  border-color: rgba(225, 29, 72, 0.55);
  box-shadow:
    0 2px 6px rgba(190, 18, 60, 0.1),
    0 12px 28px rgba(239, 68, 68, 0.28);
}

.meme-like-btn.is-preview,
.meme-like-btn.is-disabled {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #94a3b8;
  box-shadow: none;
  cursor: not-allowed;
}

.meme-like-btn.is-loading {
  cursor: wait;
}

.meme-like-btn__icon-wrap {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.meme-like-btn__icon {
  width: 22px;
  height: 22px;
}

.meme-like-btn__spinner {
  width: 20px;
  height: 20px;
  animation: meme-like-spin 0.75s linear infinite;
}

@keyframes meme-like-spin {
  to {
    transform: rotate(360deg);
  }
}

.meme-like-btn__content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  text-align: left;
}

.meme-like-btn__label {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.meme-like-btn__hint {
  margin-top: 1px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
  opacity: 0.78;
}

.meme-like-btn.is-compact .meme-like-btn__content {
  flex-direction: row;
  align-items: center;
}

.meme-like-btn.is-compact {
  min-height: 38px;
  padding: 0 14px 0 12px;
  gap: 8px;
}

.meme-like-btn.is-compact .meme-like-btn__label {
  font-size: 13px;
}

.meme-like-btn.is-compact .meme-like-btn__icon-wrap,
.meme-like-btn.is-compact .meme-like-btn__icon {
  width: 18px;
  height: 18px;
}

@media (max-width: 767px) {
  .meme-like-btn.is-compact {
    flex: 1;
    justify-content: center;
  }
}
</style>
