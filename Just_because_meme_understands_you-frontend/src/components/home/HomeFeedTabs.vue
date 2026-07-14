<template>
  <div class="home-feed-tabs">
    <div class="home-feed-tabs__main">
      <button
        v-for="tab in visibleTabs"
        :key="tab.value"
        type="button"
        class="home-feed-tabs__tab"
        :class="{ 'is-active': modelValue === tab.value }"
        @click="$emit('update:modelValue', tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>
    <button
      v-if="isLoggedIn"
      type="button"
      class="home-feed-tabs__tab home-feed-tabs__tab--following"
      :class="{ 'is-active': modelValue === 'following' }"
      @click="$emit('update:modelValue', 'following')"
    >
      关注
    </button>
    <span v-else class="home-feed-tabs__login-hint">登录后可看关注</span>
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
  },
  emits: ['update:modelValue'],
  computed: {
    visibleTabs() {
      return BASE_TABS
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
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.home-feed-tabs__main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.home-feed-tabs__tab {
  border: none;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  border-radius: 999px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.home-feed-tabs__tab:hover {
  color: var(--meme-text);
}

.home-feed-tabs__tab.is-active {
  background: var(--meme-primary);
  color: var(--meme-text-inverse);
  box-shadow: 0 6px 16px var(--meme-focus-ring);
}

.home-feed-tabs__tab--following.is-active {
  background: var(--meme-text);
  color: var(--meme-bg);
  box-shadow: var(--meme-shadow-soft);
}

.home-feed-tabs__login-hint {
  font-size: 12px;
  color: var(--meme-text-muted);
  white-space: nowrap;
}
</style>
