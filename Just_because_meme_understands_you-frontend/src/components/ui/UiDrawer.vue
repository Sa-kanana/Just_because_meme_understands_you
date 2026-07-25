<template>
  <Teleport to="body">
    <transition name="ui-drawer">
      <div v-if="modelValue" class="ui-drawer-root">
        <div class="ui-drawer__mask" @click="onMask" />
        <aside class="ui-drawer__panel" :class="[`is-${direction}`]" :style="panelStyle">
          <header v-if="title || $slots.header" class="ui-drawer__header">
            <slot name="header">
              <h3>{{ title }}</h3>
            </slot>
            <button type="button" class="ui-drawer__close" aria-label="关闭" @click="close">
              <i class="ri-close-line" />
            </button>
          </header>
          <div class="ui-drawer__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="ui-drawer__footer">
            <slot name="footer" />
          </footer>
        </aside>
      </div>
    </transition>
  </Teleport>
</template>

<script>
export default {
  name: 'UiDrawer',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
    direction: { type: String, default: 'rtl' },
    size: { type: [String, Number], default: '360px' },
    closeOnClickModal: { type: Boolean, default: true },
  },
  emits: ['update:modelValue', 'close'],
  computed: {
    panelStyle() {
      const size = typeof this.size === 'number' ? `${this.size}px` : this.size
      if (this.direction === 'ttb' || this.direction === 'btt') {
        return { height: size }
      }
      return { width: size }
    },
  },
  methods: {
    close() {
      this.$emit('update:modelValue', false)
      this.$emit('close')
    },
    onMask() {
      if (this.closeOnClickModal) this.close()
    },
  },
}
</script>

<style scoped>
.ui-drawer-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
}
.ui-drawer__mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
}
.ui-drawer__panel {
  position: absolute;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: var(--meme-bg-card, #fff);
  box-shadow: var(--meme-shadow-soft);
}
.ui-drawer__panel.is-rtl {
  right: 0;
}
.ui-drawer__panel.is-ltr {
  left: 0;
}
.ui-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--meme-border);
}
.ui-drawer__header h3 {
  margin: 0;
  font-size: 16px;
}
.ui-drawer__close {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: var(--meme-text-muted);
}
.ui-drawer__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px;
}
.ui-drawer__footer {
  padding: 12px 16px;
  border-top: 1px solid var(--meme-border);
}
.ui-drawer-enter-active,
.ui-drawer-leave-active {
  transition: opacity 0.2s ease;
}
.ui-drawer-enter-from,
.ui-drawer-leave-to {
  opacity: 0;
}
</style>
