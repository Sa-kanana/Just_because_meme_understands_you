<template>
  <button
    type="button"
    class="ui-dropdown-item"
    :class="{ 'is-divided': divided, 'is-disabled': disabled }"
    :disabled="disabled"
    @click="onClick"
  >
    <slot />
  </button>
</template>

<script>
export default {
  name: 'UiDropdownItem',
  inject: {
    uiDropdownEmit: { default: null },
  },
  props: {
    command: { type: [String, Number, Object], default: undefined },
    divided: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  methods: {
    onClick() {
      if (this.disabled) return
      if (typeof this.uiDropdownEmit === 'function') {
        this.uiDropdownEmit(this.command)
      }
    },
  },
}
</script>

<style scoped>
.ui-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--meme-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.ui-dropdown-item:hover:not(:disabled) {
  background: var(--meme-bg-muted);
}
.ui-dropdown-item.is-divided {
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--meme-border);
  border-radius: 0 0 6px 6px;
}
.ui-dropdown-item.is-disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
