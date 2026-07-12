<template>
  <section class="home-hot-tags" aria-label="热门标签">
    <div class="home-hot-tags__head">
      <h2 class="home-hot-tags__title">热门标签</h2>
    </div>
    <div ref="scroller" class="home-hot-tags__scroll">
      <button
        v-for="tag in tags"
        :key="tag.id"
        type="button"
        class="home-hot-tags__chip"
        @click="$emit('select', tag)"
      >
        #{{ tag.name }}
      </button>
    </div>
  </section>
</template>

<script>
export default {
  name: 'HomeHotTags',
  props: {
    tags: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['select'],
}
</script>

<style scoped>
.home-hot-tags {
  margin-bottom: 18px;
}

.home-hot-tags__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.home-hot-tags__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.home-hot-tags__scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
}

.home-hot-tags__scroll::-webkit-scrollbar {
  height: 6px;
}

.home-hot-tags__scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.home-hot-tags__chip {
  flex: 0 0 auto;
  border: 1px solid rgba(49, 138, 239, 0.16);
  background: rgba(49, 138, 239, 0.06);
  color: #1d4ed8;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.home-hot-tags__chip:hover {
  background: rgba(49, 138, 239, 0.12);
  border-color: rgba(49, 138, 239, 0.28);
}
</style>
