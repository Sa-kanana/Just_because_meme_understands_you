<template>
  <div class="home-feed-tabs">
    <div class="home-feed-tabs__track" role="tablist" aria-label="Feed 排序">
      <button
        v-for="tab in visibleTabs"
        :key="tab.value"
        type="button"
        role="tab"
        class="home-feed-tabs__tab"
        :class="{ 'is-active': modelValue === tab.value }"
        :aria-selected="modelValue === tab.value"
        :disabled="disabled"
        @click="selectTab(tab.value)"
      >
        {{ tab.label }}
      </button>
      <button
        v-if="isLoggedIn"
        type="button"
        role="tab"
        class="home-feed-tabs__tab"
        :class="{ 'is-active': modelValue === 'following' }"
        :aria-selected="modelValue === 'following'"
        :disabled="disabled"
        @click="selectTab('following')"
      >
        关注
      </button>
    </div>
    <span v-if="!isLoggedIn" class="home-feed-tabs__login-hint">登录后可看关注动态</span>
  </div>
</template>

<script>
const BASE_TABS = [
  { value: 'hot', label: '推荐' },
  { value: 'new', label: '最新' },
  { value: 'comments', label: '热议' },
]

export default {
  name: 'HomeFeedTabs',
  props: {
    modelValue: {
      type: String,
      default: 'hot',
    },
    isLoggedIn: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update:modelValue'],
  computed: {
    visibleTabs() {
      return BASE_TABS
    },
  },
  methods: {
    selectTab(value) {
      if (this.disabled || value === this.modelValue) return
      this.$emit('update:modelValue', value)
    },
  },
}
</script>

<style scoped>
.home-feed-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.home-feed-tabs__track {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
}

.home-feed-tabs__tab {
  border: none;
  background: transparent;
  color: var(--meme-text-secondary);
  border-radius: 999px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.home-feed-tabs__tab:hover:not(:disabled) {
  color: var(--meme-text);
}

.home-feed-tabs__tab:disabled {
  cursor: wait;
  opacity: 0.75;
}

.home-feed-tabs__tab.is-active {
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  color: #fff;
  box-shadow: 0 6px 16px var(--meme-focus-ring);
}

.home-feed-tabs__login-hint {
  font-size: 12px;
  color: var(--meme-text-muted);
  white-space: nowrap;
}
</style>
