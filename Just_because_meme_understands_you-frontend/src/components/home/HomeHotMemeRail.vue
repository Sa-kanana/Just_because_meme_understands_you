<template>
  <section class="home-hot-rail" :aria-label="title">
    <div class="meme-section-head">
      <div>
        <h2 class="meme-section-title">{{ title }}</h2>
      </div>
    </div>

    <div class="home-hot-rail__list">
      <router-link
        v-for="(item, index) in displayMemes"
        :key="item.id"
        :to="detailLocation(item.id)"
        class="home-hot-rail__row"
        :style="{ '--enter-delay': `${Math.min(index, 11) * 35}ms` }"
      >
        <span class="home-hot-rail__rank" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
        <div class="home-hot-rail__media">
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
        </div>
        <div class="home-hot-rail__copy">
          <p class="home-hot-rail__name" :title="item.name">{{ item.name || '未命名梗图' }}</p>
          <MemeCardStats
            variant="inline"
            :spread="false"
            :page-views="item.pageViews"
            :likes="item.likes"
            :comments="item.comments"
          />
        </div>
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
    title: {
      type: String,
      default: '今日热梗',
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
  padding: 20px 18px 12px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-lg);
  background: var(--meme-bg-card);
  box-shadow: 0 4px 18px rgba(27, 69, 125, 0.035);
}

.home-hot-rail .meme-section-head {
  margin-bottom: 6px;
  padding: 0 4px;
}

.home-hot-rail__list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.home-hot-rail__row {
  display: grid;
  grid-template-columns: 21px 76px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 11px 3px;
  border-bottom: 1px solid var(--meme-border);
  color: inherit;
  text-decoration: none;
  animation: hot-rail-in 0.3s ease-out both;
  animation-delay: var(--enter-delay, 0ms);
  transition: background 0.16s ease;
}

.home-hot-rail__row:last-child {
  border-bottom: none;
}

.home-hot-rail__row:hover {
  background: var(--meme-bg-muted);
}

.home-hot-rail__rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 7px;
  color: var(--meme-text-muted);
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.home-hot-rail__rank.is-top {
  color: #fff;
  background: var(--meme-primary);
}

.home-hot-rail__media {
  width: 76px;
  height: 64px;
  overflow: hidden;
  border-radius: 10px;
  background: var(--meme-bg-cover);
}

.home-hot-rail__img,
.home-hot-rail__media :deep(.ui-image),
.home-hot-rail__media :deep(.ui-image img),
.home-hot-rail__media :deep(.meme-cover-ph) {
  width: 100%;
  height: 100%;
}

.home-hot-rail__media :deep(.meme-cover-ph) {
  display: flex;
}

.home-hot-rail__media :deep(.meme-cover-ph__abstract-mark) {
  font-size: 23px;
  letter-spacing: 0;
  white-space: nowrap;
}

.home-hot-rail__media :deep(.meme-cover-ph__ring) {
  border-width: 1px;
}

.home-hot-rail__copy {
  min-width: 0;
}

.home-hot-rail__name {
  margin: 0 0 8px;
  color: var(--meme-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-hot-rail__row:hover .home-hot-rail__name {
  color: var(--meme-primary);
}

.home-hot-rail__copy :deep(.meme-card-stats) {
  gap: 9px;
}

.home-hot-rail__copy :deep(.meme-card-stats__chip) {
  gap: 3px;
  font-size: 11px;
}

.home-hot-rail__copy :deep(.meme-card-stats__icon) {
  width: 12px;
  height: 12px;
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
  .home-hot-rail__row {
    animation: none;
  }
}

@media (max-width: 860px) {
  .home-hot-rail__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 22px;
  }
}

@media (max-width: 560px) {
  .home-hot-rail__list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
