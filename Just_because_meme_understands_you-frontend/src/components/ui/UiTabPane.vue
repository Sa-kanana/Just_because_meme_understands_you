<template>
  <div v-show="active" class="ui-tab-pane">
    <slot />
  </div>
</template>

<script>
export default {
  name: 'UiTabPane',
  inject: {
    uiTabsRegister: { default: null },
    uiTabsUnregister: { default: null },
    uiTabsUpdate: { default: null },
    uiTabsActive: { default: () => '' },
  },
  props: {
    label: { type: String, default: '' },
    name: { type: [String, Number], required: true },
  },
  computed: {
    active() {
      return typeof this.uiTabsActive === 'function'
        ? this.uiTabsActive() === this.name
        : false
    },
  },
  watch: {
    label(val) {
      if (typeof this.uiTabsUpdate === 'function') {
        this.uiTabsUpdate(this.name, { label: val })
      }
    },
  },
  mounted() {
    if (typeof this.uiTabsRegister === 'function') {
      this.uiTabsRegister({ name: this.name, label: this.label })
    }
  },
  beforeUnmount() {
    if (typeof this.uiTabsUnregister === 'function') {
      this.uiTabsUnregister(this.name)
    }
  },
}
</script>
