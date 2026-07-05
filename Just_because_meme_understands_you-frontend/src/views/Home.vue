<template>
  <div class="page home-page">
    <!-- 顶部推荐 + 轮播区域 -->
    <section class="hero-section">
      <el-card class="hero-card" shadow="never">
        <el-row :gutter="20" class="hero-row">
          <!-- 左侧介绍模块 -->
          <el-col :xs="24" :md="12">
            <div class="hero-panel">
              <h2 class="page-title">只因“梗”懂你</h2>
              <p class="hero-tagline">万物皆可梗，只有我懂你。</p>
              <p class="hero-subtitle">
                欢迎来到你的互联网冲浪避风港。这里不仅有实时更新的热门梗图，更有为你量身定制的幽默内容。我们不生产梗，我们只是潮流的捕手与情感的共鸣器。
              </p>
              <p class="block-desc">
                无论你是想寻找今日份的快乐，还是想探寻热搜背后的文化源头，只因“梗”懂你，让每一次点击都精准触达你的笑点。
              </p>
            </div>
          </el-col>

          <!-- 右侧轮播图模块（接口：GET /image，仅展示 status=1） -->
          <el-col :xs="24" :md="12">
            <div class="hero-panel hero-panel-carousel">
              <el-carousel
                v-if="carouselList.length"
                class="hero-carousel"
                :height="carouselHeight + 'px'"
                :interval="5000"
              >
                <el-carousel-item
                  v-for="(item, index) in carouselList"
                  :key="getCarouselKey(item, index)"
                >
                  <div
                    class="carousel-img-wrapper"
                    :class="{ 'is-clickable': isCarouselClickable(item) }"
                    @click="handleCarouselClick(item)"
                  >
                    <el-image
                      :src="item.img_url"
                      :alt="item.title || '轮播图'"
                      fit="cover"
                      class="carousel-img"
                    >
                      <template #error>
                        <div class="carousel-img-error">{{ item.title || '加载失败' }}</div>
                      </template>
                    </el-image>
                    <div v-if="item.title" class="carousel-title" :title="item.title">{{ item.title }}</div>
                  </div>
                </el-carousel-item>
              </el-carousel>
              <div v-else-if="carouselLoading" class="carousel-loading">
                <span class="meme-loading-spinner"></span>
                <span>轮播加载中...</span>
              </div>
              <div v-else-if="carouselError" class="carousel-error">
                <el-alert :title="carouselError" type="warning" show-icon :closable="false" />
              </div>
              <div v-else class="carousel-empty">
                <span>暂无轮播图</span>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </section>

    <!-- 数据总览卡片区域 -->
    <section class="data-section">
      <el-row :gutter="16">
        <el-col
          v-for="item in dataBlocks"
          :key="item.id"
          :xs="12"
          :sm="8"
          :md="6"
          class="data-col"
        >
          <el-card shadow="hover" class="data-card">
            <div class="data-content">
              <div class="data-title">{{ item.title }}</div>
              <div class="data-value">{{ item.value }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </section>

    <!-- 梗的分页展示（每次两排，点击加载更多） -->
    <section class="meme-section">
      <h2 class="section-title">梗列表</h2>
      <div class="meme-list-wrap">
        <el-row :gutter="16" class="meme-row">
          <el-col
            v-for="item in memeList"
            :key="item.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
            class="meme-col"
          >
            <router-link
              :to="{ name: 'memeDetail', params: { id: item.id } }"
              class="meme-card-link"
            >
              <el-card
                shadow="hover"
                class="meme-card"
                :body-style="{ padding: 0, display: 'flex', flexDirection: 'column', height: '100%' }"
              >
                <!-- 图片区（比例 2）：圆角、叠字 浏览量/评论/点赞 -->
                <div class="meme-img-wrap">
                  <el-image
                    v-if="item.image"
                    :src="item.image"
                    :alt="item.name"
                    fit="cover"
                    class="meme-img"
                    lazy
                  >
                    <template #error>
                      <div class="meme-img-error">
                        <span>图片加载失败</span>
                      </div>
                    </template>
                  </el-image>
                  <div v-else class="meme-img-error">
                    <span>暂无封面</span>
                  </div>
                  <div class="meme-img-overlay">
                    <div class="meme-overlay-left">
                      <span class="meme-overlay-item" title="浏览量">👁 {{ formatNum(item.pageViews) }}</span>
                      <span class="meme-overlay-item" title="评论">💬 {{ formatNum(item.comments) }}</span>
                    </div>
                    <div class="meme-overlay-right">
                      <span class="meme-overlay-item" title="点赞">👍 {{ formatNum(item.likes) }}</span>
                    </div>
                  </div>
                </div>
                <!-- 信息区（比例 1）：背景透明，标题、标签、日期 -->
                <div class="meme-info">
                  <h3 class="meme-name" :title="item.name">{{ item.name }}</h3>
                  <div v-if="tags(item).length" class="meme-tags">
                    <el-tag
                      v-for="tag in tags(item).slice(0, 3)"
                      :key="tag.id"
                      size="small"
                      type="info"
                      class="meme-tag"
                    >
                      {{ tag.name }}
                    </el-tag>
                  </div>
                  <div class="meme-meta">
                    {{ formatDate(item.releaseTime || item.updateTime) || '—' }}
                  </div>
                </div>
              </el-card>
            </router-link>
          </el-col>
        </el-row>
        <div v-if="memeLoading" class="meme-loading">
          <span class="meme-loading-spinner"></span>
          <span>加载中...</span>
        </div>
        <div v-else-if="memeNoMore && memeList.length" class="meme-nomore">
          没有更多了
        </div>
        <div
          v-else-if="memeList.length && !memeLoading && !memeLoadError"
          class="meme-load-more"
        >
          <el-button
            type="primary"
            class="meme-load-more-btn"
            :loading="memeLoading"
            @click="loadMoreMeme"
          >
            加载更多
          </el-button>
        </div>
        <div v-else-if="memeLoadError" class="meme-error">
          <el-alert
            :title="memeLoadError"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>
        <div v-else-if="!memeList.length && !memeLoading" class="meme-empty">
          <el-empty description="暂无梗图数据" />
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { getMemeList } from '@/api/meme'
import { getHomeImages } from '@/api/homeImage'

/** 跳转类型：0-无跳转，1-内部梗ID，2-外部链接 */
const TARGET_TYPE = { NONE: 0, INTERNAL: 1, EXTERNAL: 2 }

export default {
  name: 'HomePage',
  data() {
    return {
      carouselList: [],
      carouselLoading: false,
      carouselError: '',
      // 模拟后端返回的数据结构，后续可直接替换为真实接口数据
      dataBlocks: [
        { id: 1, title: '今日访问量', value: 1234 },
        { id: 2, title: '活跃用户数', value: 256 },
        { id: 3, title: '梗图总数', value: 89 },
        { id: 5, title: '评论总数', value: 342 },
      ],
      // 梗分页列表（每次加载 16 条 = 4 列 × 4 行）
      memeList: [],
      memePage: 1,
      memePageSize: 16,
      memeLoading: false,
      memeNoMore: false,
      memeLoadError: '',
      carouselHeight: 440,
    }
  },
  mounted() {
    this.loadHomeImages()
    this.loadMemeList()
    this.updateCarouselHeight()
    window.addEventListener('resize', this.updateCarouselHeight)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.updateCarouselHeight)
  },
  methods: {
    updateCarouselHeight() {
      const w = typeof window !== 'undefined' ? window.innerWidth : 1200
      this.carouselHeight = w < 768 ? 280 : w < 1200 ? 380 : 440
    },
    getCarouselKey(item, index) {
      return item.id != null ? item.id : `img-${index}-${item.img_url || ''}`
    },
    isCarouselClickable(item) {
      const type = Number(item.target_type)
      if (type === TARGET_TYPE.NONE) return false
      if (type === TARGET_TYPE.INTERNAL) return !!item.target_value
      if (type === TARGET_TYPE.EXTERNAL) return !!item.target_value
      return false
    },
    handleCarouselClick(item) {
      const type = Number(item.target_type)
      const value = (item.target_value || '').trim()
      if (type === TARGET_TYPE.INTERNAL && value) {
        this.$router.push({ name: 'memeDetail', params: { id: value } })
      } else if (type === TARGET_TYPE.EXTERNAL && value) {
        window.open(value, '_blank', 'noopener,noreferrer')
      }
    },
    async loadHomeImages() {
      this.carouselLoading = true
      this.carouselError = ''
      try {
        this.carouselList = await getHomeImages()
      } catch (e) {
        this.carouselError = e.message || '轮播图加载失败'
        this.carouselList = []
      } finally {
        this.carouselLoading = false
      }
    },
    tags(item) {
      return item.memeTag || item.label || []
    },
    async loadMemeList() {
      if (this.memeLoading) return
      this.memeLoading = true
      this.memeLoadError = ''
      this.memePage = 1
      this.memeList = []
      try {
        const list = await getMemeList({ page: this.memePage })
        this.memeList = list
        // 不足一页（16 条）说明没有更多了
        this.memeNoMore = list.length < this.memePageSize
      } catch (e) {
        this.memeLoadError = e.message || '加载梗图列表失败'
        this.memeList = []
      } finally {
        this.memeLoading = false
      }
    },
    async loadMoreMeme() {
      if (this.memeLoading || this.memeNoMore || this.memeLoadError) return
      this.memeLoading = true
      try {
        const nextPage = this.memePage + 1
        const list = await getMemeList({ page: nextPage })
        const existingIds = new Set(this.memeList.map((item) => item.id))
        const newItems = list.filter((item) => !existingIds.has(item.id))
        this.memeList = this.memeList.concat(newItems)
        this.memePage = nextPage
        // 本次返回不足 16 条，或没有新增项，说明到底了
        this.memeNoMore = list.length < this.memePageSize || newItems.length === 0
      } catch (_) {
        this.memeNoMore = true
      } finally {
        this.memeLoading = false
      }
    },
    formatNum(num) {
      if (num == null) return '0'
      const n = Number(num)
      if (n >= 1e8) return (n / 1e8).toFixed(1) + '亿'
      if (n >= 1e4) return (n / 1e4).toFixed(1) + '万'
      return String(n)
    },
    /** 日期只显示 YYYY-MM-DD，不显示时分秒 */
    formatDate(str) {
      if (!str) return ''
      const s = String(str).trim()
      const match = s.match(/^(\d{4}-\d{2}-\d{2})/)
      return match ? match[1] : ''
    },
  },
}
</script>

<style scoped>
.page {
  padding: 12px 32px 24px;
}

.hero-section {
  margin-bottom: 40px;
}

.hero-card {
  border-radius: var(--meme-radius-xl, 24px);
  padding: 16px 12px 16px 20px;
  border: 1px solid var(--meme-border, #e5e7eb);
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.08), 0 2px 8px rgba(49, 138, 239, 0.06);
}

.hero-row {
  align-items: stretch;
  margin: 0;
}

.hero-panel {
  border-radius: 18px;
  border: none;
  padding: 20px 24px 24px;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* 左侧主标题：更大气 */
.page-title {
  margin: 0 0 10px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.03em;
  color: var(--meme-text, #111827);
  line-height: 1.3;
}

.hero-tagline {
  margin: 0 0 18px;
  font-size: 19px;
  font-weight: 600;
  color: var(--meme-primary, #318AEF);
  line-height: 1.45;
  letter-spacing: 0.02em;
}

.hero-subtitle {
  margin: 0 0 14px;
  color: #4b5563;
  font-size: 15px;
  line-height: 1.75;
}

.block-title {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 700;
}

.block-desc {
  margin: 0 0 8px;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.65;
}

/* 右侧轮播区：无内边距，图片贴满，整体更高 */
.hero-panel-carousel {
  min-height: 380px;
  justify-content: stretch;
  padding: 0;
  border-radius: var(--meme-radius-lg, 16px);
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.08);
}

.hero-carousel {
  margin: 0;
  border-radius: 0;
  overflow: hidden;
  border: none;
  box-shadow: none;
  flex: 1;
  min-height: 320px;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.hero-carousel :deep(.el-carousel__container) {
  height: 100%;
  flex: 1;
}

.hero-carousel :deep(.el-carousel__item) {
  height: 100%;
}

/* 轮播箭头与指示器：更大、更清晰，贴合主题 */
.hero-carousel :deep(.el-carousel__arrow) {
  width: 44px;
  height: 44px;
  background-color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.08);
  color: #374151;
  font-size: 18px;
}
.hero-carousel :deep(.el-carousel__arrow:hover) {
  background-color: #fff;
  border-color: rgba(0, 0, 0, 0.12);
  color: #111827;
}
.hero-carousel :deep(.el-carousel__indicators) {
  margin-top: 14px;
}
.hero-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  background-color: #6b7280;
  width: 24px;
  border-radius: 4px;
}
.hero-carousel :deep(.el-carousel__button) {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #d1d5db;
  transition: width 0.2s ease, border-radius 0.2s ease;
}

.gallery-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.gallery-item {
  margin-bottom: 8px;
}

.gallery-image {
  width: 100%;
  border-radius: 16px;
}

/* 轮播图：填满容器，大图更大气 */
.carousel-img-wrapper {
  width: 100%;
  height: 100%;
  min-height: 260px;
  overflow: hidden;
  position: relative;
  background: #f3f4f6;
}

.carousel-img-wrapper.is-clickable {
  cursor: pointer;
}

.carousel-img-wrapper :deep(.el-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.carousel-img-wrapper :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.carousel-title {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 14px 20px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.5), transparent);
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
}

/* 数据卡片与主题一致 */
.data-card :deep(.el-card__body) {
  border-radius: var(--meme-radius-md, 12px);
}

.carousel-img-error {
  color: #9ca3af;
  font-size: 14px;
}

.carousel-loading,
.carousel-error,
.carousel-empty {
  min-height: 280px;
  height: 440px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--meme-radius-lg, 16px);
  background: #f3f4f6;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

@media (max-width: 768px) {
  .carousel-loading,
  .carousel-error,
  .carousel-empty {
    height: 280px;
  }
}

.carousel-loading {
  gap: 8px;
  color: #6b7280;
  font-size: 14px;
}

.carousel-empty {
  color: #9ca3af;
  font-size: 14px;
}

.intro-title,
.gallery-title {
  color: #52c7b8;
}

.data-section {
  margin-top: 8px;
}

.data-col {
  margin-bottom: 16px;
}

.data-card {
  border-radius: 24px;
  height: 120px;
  box-sizing: border-box;
  background-color: #ffffff;
  transition: box-shadow 0.15s ease, transform 0.1s ease, background-color 0.15s ease;
}

.data-card:hover {
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
  background-color: #f9fafb;
}

.data-content {
  text-align: center;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.data-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.data-value {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
}

.data-desc {
  color: #111827;
  font-size: 13px;
  line-height: 1.5;
}

/* 梗的分页展示区域 */
.meme-section {
  margin-top: 16px;
}

.section-title {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 600;
  color: #111827;
}

.meme-list-wrap {
  min-height: 120px;
}

.meme-load-more {
  padding: 24px 0;
  width: 100%;
}

/* 加载更多按钮：与上方卡片行同宽，反馈效果与卡片一致 */
.meme-load-more-btn {
  width: 100% !important;
  min-height: 48px;
  padding: 14px 24px !important;
  font-size: 16px !important;
  font-weight: 600;
  border-radius: 16px !important;
  transition: box-shadow 0.2s ease, transform 0.15s ease;
}

.meme-load-more-btn:hover,
.meme-load-more-btn:focus {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.15);
  transform: translateY(-2px);
}

.meme-row {
  margin: 0 -8px;
}

.meme-col {
  margin-bottom: 16px;
}

.meme-card-link {
  display: block;
  text-decoration: none;
  color: inherit;
}

/* 图片:信息区 = 2:1，总高 300px → 图 200px，信息 100px */
.meme-card {
  border-radius: 16px;
  overflow: hidden;
  transition: box-shadow 0.2s ease, transform 0.15s ease;
  display: flex;
  flex-direction: column;
  height: 300px;
}

.meme-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 0;
  background: transparent;
}

.meme-card:hover {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
  transform: translateY(-2px);
}

/* 图片区：占 2 份高度 */
.meme-img-wrap {
  position: relative;
  width: 100%;
  height: 200px;
  flex-shrink: 0;
  background: #f3f4f6;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
}

.meme-img {
  width: 100%;
  height: 100%;
  display: block;
}

.meme-img-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 14px;
}

/* 图片叠字：半透明深色底、白字，左下 浏览量/评论、右下 点赞 */
.meme-img-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55), transparent);
  pointer-events: none;
}

.meme-overlay-left,
.meme-overlay-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meme-overlay-item {
  font-size: 12px;
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.4);
}

/* 信息区：占 1 份高度，背景透明 */
.meme-info {
  flex: 1;
  min-height: 0;
  height: 100px;
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  background: transparent;
}

.meme-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meme-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
  min-height: 0;
}

.meme-tag {
  margin: 0;
}

.meme-meta {
  font-size: 12px;
  color: #9ca3af;
  margin-top: auto;
}

.meme-loading,
.meme-nomore,
.meme-load-more,
.meme-error,
.meme-empty {
  text-align: center;
  padding: 24px;
  color: #6b7280;
  font-size: 14px;
}

.meme-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.meme-loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid #e5e7eb;
  border-top-color: #52c7b8;
  border-radius: 50%;
  animation: meme-spin 0.8s linear infinite;
}

@keyframes meme-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
