<template>
  <form class="ai-composer" @submit.prevent="onSubmit">
    <el-input
      ref="inputRef"
      v-model="innerValue"
      type="textarea"
      :rows="2"
      :autosize="{ minRows: 2, maxRows: 5 }"
      maxlength="2000"
      show-word-limit
      resize="none"
      placeholder="描述你想表达的场景，例如：同事甩锅时的表情包…"
      :disabled="disabled"
      @keydown="onKeydown"
    />
    <div class="ai-composer__actions">
      <el-button
        v-if="streaming"
        class="ai-composer__stop"
        @click="$emit('stop')"
      >
        停止
      </el-button>
      <el-button
        type="primary"
        native-type="submit"
        :loading="streaming"
        :disabled="disabled || !canSend"
      >
        发送
      </el-button>
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
      if (comp && typeof comp.focus === 'function') comp.focus()
    },
  },
}
</script>

<style scoped>
.ai-composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 18px 18px;
  border-top: 1px solid var(--meme-border);
  background: var(--meme-bg-elevated);
}

.ai-composer :deep(.el-textarea__inner) {
  border-radius: var(--meme-radius-md);
  box-shadow: 0 0 0 1px var(--meme-border) inset;
  background: var(--meme-bg-card);
}

.ai-composer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
