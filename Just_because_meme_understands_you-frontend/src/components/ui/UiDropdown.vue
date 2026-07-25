<template>
  <div ref="root" class="ui-dropdown" @mouseenter="onEnter" @mouseleave="onLeave">
    <div class="ui-dropdown__trigger" @click.capture.stop="onTriggerClick">
      <slot />
    </div>
    <div v-show="open" class="ui-dropdown__menu" :class="[`is-${placement}`]" @click.stop>
      <slot name="dropdown" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiDropdown',
  props: {
    trigger: { type: String, default: 'hover' },
    placement: { type: String, default: 'bottom-end' },
  },
  emits: ['command'],
  data() {
    return { open: false, leaveTimer: null }
  },
  provide() {
    return {
      uiDropdownEmit: (cmd) => {
        this.$emit('command', cmd)
        this.open = false
      },
    }
  },
  watch: {
    open(val) {
      if (this.trigger !== 'click') return
      if (val) {
        this.$nextTick(() => {
          document.addEventListener('click', this.onDocClick, true)
        })
      } else {
        document.removeEventListener('click', this.onDocClick, true)
      }
    },
  },
  beforeUnmount() {
    this.clearLeave()
    document.removeEventListener('click', this.onDocClick, true)
  },
  methods: {
    onTriggerClick() {
      if (this.trigger === 'click') this.open = !this.open
    },
    onDocClick(e) {
      const root = this.$refs.root
      if (!root || root.contains(e.target)) return
      this.open = false
    },
    onEnter() {
      this.clearLeave()
      if (this.trigger === 'hover') this.open = true
    },
    onLeave() {
      if (this.trigger !== 'hover') return
      this.leaveTimer = window.setTimeout(() => {
        this.open = false
      }, 120)
    },
    clearLeave() {
      if (this.leaveTimer != null) {
        window.clearTimeout(this.leaveTimer)
        this.leaveTimer = null
      }
    },
  },
}
</script>

<style scoped>
.ui-dropdown {
  position: relative;
  display: inline-flex;
}
.ui-dropdown__trigger {
  display: inline-flex;
  cursor: pointer;
}
.ui-dropdown__menu {
  position: absolute;
  z-index: 1200;
  min-width: 160px;
  padding: 6px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-sm, 8px);
  background: var(--meme-bg-elevated, #fff);
  box-shadow: var(--meme-shadow-soft);
}
.ui-dropdown__menu.is-bottom-end {
  top: calc(100% + 6px);
  right: 0;
}
.ui-dropdown__menu.is-bottom-start {
  top: calc(100% + 6px);
  left: 0;
}
</style>
