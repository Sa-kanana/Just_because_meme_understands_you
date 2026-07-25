<template>
  <div class="ui-tabs">
    <div class="ui-tabs__nav" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.name"
        type="button"
        role="tab"
        class="ui-tabs__item"
        :class="{ 'is-active': modelValue === tab.name }"
        @click="$emit('update:modelValue', tab.name); $emit('tab-change', tab.name)"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="ui-tabs__body">
      <slot />
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiTabs',
  props: {
    modelValue: { type: [String, Number], default: '' },
  },
  emits: ['update:modelValue', 'tab-change'],
  data() {
    return { tabs: [] }
  },
  provide() {
    return {
      uiTabsRegister: (tab) => {
        const idx = this.tabs.findIndex((t) => t.name === tab.name)
        if (idx >= 0) {
          this.tabs.splice(idx, 1, { ...this.tabs[idx], ...tab })
          return
        }
        this.tabs.push(tab)
      },
      uiTabsUpdate: (name, patch) => {
        const idx = this.tabs.findIndex((t) => t.name === name)
        if (idx < 0) return
        this.tabs.splice(idx, 1, { ...this.tabs[idx], ...patch })
      },
      uiTabsUnregister: (name) => {
        this.tabs = this.tabs.filter((t) => t.name !== name)
      },
      uiTabsActive: () => this.modelValue,
    }
  },
}
</script>

<style scoped>
.ui-tabs__nav {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--meme-border);
  margin-bottom: 12px;
}
.ui-tabs__item {
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 14px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.ui-tabs__item.is-active {
  color: var(--meme-primary);
  border-bottom-color: var(--meme-primary);
  font-weight: 600;
}
</style>
