<template>
  <section class="home-hot-rail" aria-label="今日热梗">
    <div class="home-hot-rail__head">
      <h2 class="home-hot-rail__title">今日热梗</h2>
      <span class="home-hot-rail__hint">横滑查看更多</span>
    </div>
    <div class="home-hot-rail__scroll">
      <router-link
        v-for="item in memes"
        :key="item.id"
        :to="{ name: 'memeDetail', params: { id: item.id } }"
        class="home-hot-rail__card"
      >
        <div class="home-hot-rail__cover">
          <el-image
            v-if="item.image"
            :src="item.image"
            :alt="item.name"
            fit="cover"
            class="home-hot-rail__img"
            lazy
          >
            <template #error>
              <div class="home-hot-rail__placeholder">加载失败</div>
            </template>
          </el-image>
          <div v-else class="home-hot-rail__placeholder">暂无封面</div>
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

export default {
  name: 'HomeHotMemeRail',
  components: {
    MemeCardStats,
  },
  props: {
    memes: {
      type: Array,
      default: () => [],
    },
  },
}
</script>

<style scoped>
.home-hot-rail {
  margin-bottom: 20px;
}

.home-hot-rail__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.home-hot-rail__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.home-hot-rail__hint {
  font-size: 12px;
  color: #94a3b8;
}

.home-hot-rail__scroll {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 6px;
  scrollbar-width: thin;
}

.home-hot-rail__scroll::-webkit-scrollbar {
  height: 6px;
}

.home-hot-rail__scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.home-hot-rail__card {
  flex: 0 0 140px;
  text-decoration: none;
  color: inherit;
}

.home-hot-rail__cover {
  position: relative;
  height: 94px;
  border-radius: 14px;
  overflow: hidden;
  background: #eef2f7;
}

.home-hot-rail__img,
.home-hot-rail__cover :deep(.el-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.home-hot-rail__shade {
  position: absolute;
  inset: auto 0 0 0;
  height: 52px;
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

.home-hot-rail__placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #94a3b8;
}

.home-hot-rail__name {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
