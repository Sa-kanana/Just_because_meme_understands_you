<template>
  <section class="home-hot-tags" aria-label="热门标签">
    <div class="meme-section-head">
      <div>
        <p class="meme-section-kicker">TRENDING</p>
        <h2 class="meme-section-title">热门标签</h2>
      </div>
    </div>
    <div ref="scroller" class="home-hot-tags__scroll">
      <button
        v-for="tag in tags"
        :key="tag.id"
        type="button"
        class="home-hot-tags__chip"
        @click="$emit('select', tag)"
      >
        <span class="home-hot-tags__hash">#</span>{{ tag.name }}
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
  margin-bottom: 10px;
}

.home-hot-tags__scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 0;
  scrollbar-width: none;
}

.home-hot-tags__scroll::-webkit-scrollbar {
  display: none;
}

.home-hot-tags__chip {
  flex: 0 0 auto;
  border: 1px solid var(--meme-border);
  background: color-mix(in srgb, var(--meme-bg-elevated) 88%, transparent);
  color: var(--meme-text-secondary);
  border-radius: 999px;
  padding: 5px 11px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
}

.home-hot-tags__hash {
  color: var(--meme-primary);
  margin-right: 1px;
}

.home-hot-tags__chip:hover {
  background: var(--meme-primary-soft);
  border-color: var(--meme-border-accent);
  color: var(--meme-primary);
  transform: translateY(-1px);
  box-shadow: var(--meme-shadow-soft);
}
</style>
