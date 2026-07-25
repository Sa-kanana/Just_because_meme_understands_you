<template>
  <form class="ai-composer" @submit.prevent="onSubmit">
    <div class="ai-composer__dock" :class="{ 'is-focused': focused }">
      <vs-input
        ref="inputRef"
        v-model="innerValue"
        type="textarea"
        maxlength="2000"
        resize="none"
        placeholder="描述你想表达的场景，例如：同事甩锅时的表情包…"
        :disabled="disabled"
        block
        class="ai-composer__input"
        @keydown="onKeydown"
        @focus="focused = true"
        @blur="focused = false"
      />
      <div class="ai-composer__actions">
        <span class="ai-composer__tip">
          <i class="ri-command-line" aria-hidden="true" />
          Enter 发送 · Shift+Enter 换行
        </span>
        <div class="ai-composer__btns">
          <button
            v-if="streaming"
            type="button"
            class="ai-composer__stop"
            @click="$emit('stop')"
          >
            <i class="ri-stop-circle-line" aria-hidden="true" />
            停止
          </button>
          <button
            type="button"
            class="ai-composer__send"
            :class="{ 'is-disabled': disabled || !canSend || streaming }"
            :disabled="disabled || !canSend || streaming"
            @click="onSubmit"
          >
            <i v-if="!streaming" class="ri-send-plane-2-fill" aria-hidden="true" />
            <i v-else class="ri-loader-4-line is-spinning" aria-hidden="true" />
            {{ streaming ? '生成中' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </form>
</template>

<script>
export default {
  name: 'AiComposer',
  props: {
    modelValue: { type: String, default: '' },
    streaming: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'submit', 'stop'],
  data() {
    return { focused: false }
  },
  computed: {
    innerValue: {
      get() {
        return this.modelValue
      },
      set(val) {
        this.$emit('update:modelValue', val)
      },
    },
    canSend() {
      return String(this.modelValue || '').trim().length > 0
    },
  },
  methods: {
    onKeydown(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        this.onSubmit()
      }
    },
    onSubmit() {
      if (this.disabled || this.streaming || !this.canSend) return
      this.$emit('submit', String(this.modelValue || '').trim())
    },
    focus() {
      const comp = this.$refs.inputRef
      if (comp && typeof comp.focus === 'function') {
        comp.focus()
        return
      }
      const el = comp?.$el?.querySelector?.('textarea')
      el?.focus?.()
    },
  },
}
</script>

<style scoped>
.ai-composer {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  padding: 6px 18px 16px;
  background: linear-gradient(
    180deg,
    transparent 0%,
    color-mix(in srgb, var(--meme-bg-card) 55%, transparent) 28%,
    color-mix(in srgb, var(--meme-bg-card) 92%, transparent) 100%
  );
}

.ai-composer__dock {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 14%, var(--meme-border));
  background: color-mix(in srgb, var(--meme-bg-elevated) 94%, transparent);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 14px 36px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.ai-composer__dock.is-focused {
  border-color: var(--meme-primary);
  box-shadow:
    0 0 0 3px var(--meme-focus-ring),
    0 12px 28px rgba(15, 23, 42, 0.08);
}

.ai-composer__input {
  width: 100%;
}

.ai-composer :deep(.vs-input__wrapper),
.ai-composer :deep(.vs-input__original),
.ai-composer :deep(textarea) {
  width: 100%;
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 2px 0 !important;
  min-height: 52px;
  max-height: 128px;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text) !important;
}

.ai-composer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ai-composer__tip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--meme-text-muted);
}

.ai-composer__btns {
  display: flex;
  gap: 8px;
}

.ai-composer__stop {
  height: 36px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 11px;
  border: 1px solid color-mix(in srgb, var(--meme-danger, #e11d48) 35%, var(--meme-border));
  background: var(--meme-bg-elevated);
  color: var(--meme-danger, #e11d48);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.ai-composer__stop:hover {
  background: color-mix(in srgb, var(--meme-danger, #e11d48) 8%, var(--meme-bg-elevated));
}

.ai-composer__send {
  min-width: 92px;
  height: 36px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: 11px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 6px 16px var(--meme-focus-ring);
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease, box-shadow 0.15s ease;
}

.ai-composer__send:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.03);
}

.ai-composer__send.is-disabled,
.ai-composer__send:disabled {
  cursor: not-allowed;
  transform: none;
  filter: none;
  box-shadow: none;
  background: color-mix(in srgb, var(--meme-primary) 42%, var(--meme-border));
  color: #fff;
}

.is-spinning {
  animation: ai-composer-spin 0.8s linear infinite;
}

@keyframes ai-composer-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 560px) {
  .ai-composer__tip {
    display: none;
  }
}
</style>
