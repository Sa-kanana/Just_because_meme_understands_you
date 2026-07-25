<template>
  <section class="home-hot-rail" aria-label="今日热梗">
    <div class="meme-section-head">
      <div>
        <p class="meme-section-kicker">HOT TODAY</p>
        <h2 class="meme-section-title">今日热梗</h2>
      </div>
    </div>

    <div class="home-hot-rail__grid">
      <router-link
        v-for="(item, index) in displayMemes"
        :key="item.id"
        :to="detailLocation(item.id)"
        class="home-hot-rail__card"
        :style="{ '--enter-delay': `${Math.min(index, 11) * 35}ms` }"
      >
        <div class="home-hot-rail__cover">
          <span class="home-hot-rail__rank" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
          <ui-image
            v-if="coverOf(item) && !coverErrors[item.id]"
            :src="coverOf(item)"
            :alt="item.name"
            fit="cover"
            class="home-hot-rail__img"
            lazy
            @error="onCoverError(item.id)"
          />
          <MemeCoverPlaceholder
            v-else
            :name="item.name"
            :seed="item.id"
            abstract
          />
          <div class="home-hot-rail__shade" aria-hidden="true" />
          <MemeCardStats
            variant="overlay"
            spread
            :page-views="item.pageViews"
            :likes="item.likes"
            :comments="item.comments"
          />
        </div>
        <p class="home-hot-rail__name" :title="item.name">{{ item.name || '未命名梗图' }}</p>
      </router-link>
    </div>
  </section>
</template>

<script>
import MemeCardStats from '@/components/meme/MemeCardStats.vue'
import MemeCoverPlaceholder from '@/components/meme/MemeCoverPlaceholder.vue'
import { buildMemeDetailLocation } from '@/utils/pageBreadcrumb'

export default {
  name: 'HomeHotMemeRail',
  components: {
    MemeCardStats,
    MemeCoverPlaceholder,
  },
  props: {
    memes: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      coverErrors: {},
    }
  },
  computed: {
    displayMemes() {
      return Array.isArray(this.memes) ? this.memes : []
    },
  },
  methods: {
    coverOf(item) {
      const url = item && item.image != null ? String(item.image).trim() : ''
      return url
    },
    onCoverError(id) {
      this.coverErrors = { ...this.coverErrors, [id]: true }
    },
    detailLocation(id) {
      return buildMemeDetailLocation(id, { from: 'hot' })
    },
  },
}
</script>

<style scoped>
.home-hot-rail {
  margin-bottom: 12px;
}

.home-hot-rail__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.home-hot-rail__card {
  min-width: 0;
  text-decoration: none;
  color: inherit;
  animation: hot-rail-in 0.32s ease-out both;
  animation-delay: var(--enter-delay, 0ms);
  transition: transform 0.18s ease;
}

.home-hot-rail__card:hover {
  transform: translateY(-2px);
}

.home-hot-rail__cover {
  position: relative;
  aspect-ratio: 2 / 1;
  border-radius: 10px;
  overflow: hidden;
  background: var(--meme-bg-cover);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.07);
  transition: box-shadow 0.2s ease;
}

.home-hot-rail__card:hover .home-hot-rail__cover {
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.12);
}

.home-hot-rail__rank {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(8px);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.home-hot-rail__rank.is-top {
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 4px 12px var(--meme-focus-ring);
}

.home-hot-rail__img,
.home-hot-rail__cover :deep(.ui-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.home-hot-rail__shade {
  position: absolute;
  inset: auto 0 0 0;
  height: 48%;
  background: linear-gradient(to top, rgba(15, 23, 42, 0.72), transparent);
  pointer-events: none;
}

.home-hot-rail__cover :deep(.meme-card-stats) {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 8px;
  z-index: 1;
}

.home-hot-rail__name {
  margin: 6px 2px 0;
  min-height: 1.3em;
  font-size: 12px;
  font-weight: 650;
  color: var(--meme-text);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@keyframes hot-rail-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-hot-rail__card {
    animation: none;
  }
}

@media (max-width: 960px) {
  .home-hot-rail__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .home-hot-rail__grid {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
}
</style>
