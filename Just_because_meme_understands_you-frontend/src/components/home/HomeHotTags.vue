<template>
  <section class="home-hot-tags" aria-label="热门标签">
    <div class="home-hot-tags__intro">
      <span class="home-hot-tags__icon" aria-hidden="true">
        <i class="ri-hashtag" />
      </span>
      <div class="home-hot-tags__copy">
        <h2 class="meme-section-title">热门标签</h2>
        <p>发现你感兴趣的梗</p>
      </div>
    </div>
    <div ref="scroller" class="home-hot-tags__scroll" role="list" aria-label="标签列表">
      <div
        v-for="tag in tags"
        :key="tag.id"
        class="home-hot-tags__item"
        :class="{ 'is-followed': followedTagIds.includes(Number(tag.id)) }"
        role="listitem"
      >
        <button
          type="button"
          class="home-hot-tags__chip"
          :aria-label="`查看${tag.name}标签的梗图`"
          @click="$emit('select', tag)"
        >
          <span class="home-hot-tags__hash">#</span>{{ tag.name }}
        </button>
        <button
          type="button"
          class="home-hot-tags__follow"
          :aria-pressed="followedTagIds.includes(Number(tag.id))"
          :aria-busy="busyTagIds.includes(Number(tag.id))"
          :disabled="busyTagIds.includes(Number(tag.id))"
          :aria-label="`${followedTagIds.includes(Number(tag.id)) ? '取消关注' : '关注'}${tag.name}`"
          @click="$emit('toggle-follow', tag)"
        >
          <i
            :class="followedTagIds.includes(Number(tag.id)) ? 'ri-check-line' : 'ri-add-line'"
            aria-hidden="true"
          />
          <span>{{ followedTagIds.includes(Number(tag.id)) ? '已关注' : '关注' }}</span>
        </button>
      </div>
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
    followedTagIds: { type: Array, default: () => [] },
    busyTagIds: { type: Array, default: () => [] },
  },
  emits: ['select', 'toggle-follow'],
}
</script>

<style scoped>
.home-hot-tags {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 76px;
  margin-bottom: 16px;
  padding: 12px 20px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-lg);
  background: linear-gradient(
    112deg,
    var(--meme-bg-card) 0%,
    color-mix(in srgb, var(--meme-primary-soft) 34%, var(--meme-bg-card)) 100%
  );
  box-shadow: 0 6px 22px rgba(27, 69, 125, 0.045);
}

.home-hot-tags__intro {
  display: flex;
  align-items: center;
  gap: 11px;
  flex: 0 0 auto;
}

.home-hot-tags__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border: 1px solid var(--meme-border-accent);
  border-radius: 13px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.home-hot-tags__icon i {
  font-size: 20px;
}

.home-hot-tags__copy .meme-section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 750;
  line-height: 1.4;
  white-space: nowrap;
}

.home-hot-tags__copy p {
  margin: 2px 0 0;
  color: var(--meme-text-muted);
  font-size: 11px;
  line-height: 1.4;
  white-space: nowrap;
}

.home-hot-tags__scroll {
  display: flex;
  align-items: center;
  gap: 11px;
  flex: 1 1 auto;
  min-width: 0;
  overflow-x: auto;
  padding: 4px 2px 6px;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
}

.home-hot-tags__scroll::-webkit-scrollbar {
  display: none;
}

.home-hot-tags__chip {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: var(--meme-text);
  border-radius: 999px;
  padding: 7px 10px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s ease, color 0.16s ease;
}

.home-hot-tags__item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex: 0 0 auto;
  padding: 3px;
  border: 1px solid var(--meme-border);
  border-radius: 999px;
  background: var(--meme-bg-elevated);
  box-shadow: 0 2px 7px rgba(27, 69, 125, 0.035);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.home-hot-tags__item:hover {
  border-color: var(--meme-border-accent);
  box-shadow: 0 5px 13px rgba(27, 69, 125, 0.08);
  transform: translateY(-1px);
}

.home-hot-tags__hash {
  color: var(--meme-primary);
  margin-right: 3px;
  font-size: 14px;
  font-weight: 800;
}

.home-hot-tags__chip:hover {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.home-hot-tags__chip:focus-visible,
.home-hot-tags__follow:focus-visible {
  outline: 2px solid var(--meme-primary);
  outline-offset: 2px;
}

.home-hot-tags__follow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 30px;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}

.home-hot-tags__follow i {
  font-size: 13px;
}

.home-hot-tags__follow:hover:not(:disabled) {
  border-color: var(--meme-border-accent);
  background: color-mix(in srgb, var(--meme-primary-soft) 72%, var(--meme-bg-card));
}

.home-hot-tags__item.is-followed .home-hot-tags__follow {
  background: var(--meme-success-soft);
  color: var(--meme-success);
}

.home-hot-tags__follow:disabled {
  opacity: 0.55;
  cursor: wait;
}

@media (max-width: 520px) {
  .home-hot-tags {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
    min-height: 0;
    padding: 13px 15px 11px;
  }

  .home-hot-tags__intro {
    gap: 9px;
  }

  .home-hot-tags__icon {
    width: 34px;
    height: 34px;
    border-radius: 11px;
  }

  .home-hot-tags__icon i {
    font-size: 18px;
  }

  .home-hot-tags__copy p {
    display: none;
  }

  .home-hot-tags__scroll {
    width: 100%;
    gap: 8px;
    margin-inline: -2px;
  }
}
</style>
