<template>
  <div class="page search-page">
    <AppBreadcrumb :items="breadcrumbItems" />

    <!-- 排序筛选栏：参考 B 站式横向 tab -->
    <div class="search-toolbar">
      <div class="sort-tabs">
        <button
          v-for="tab in sortTabs"
          :key="tab.value"
          type="button"
          class="sort-tab"
          :class="{ active: sortBy === tab.value }"
          @click="changeSort(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- 结果网格 -->
    <div v-if="!keyword" class="search-empty-tip">
      <el-empty description="请输入关键字进行搜索" />
    </div>
    <template v-else>
      <div v-if="loading" class="meme-loading">
        <span class="meme-loading-spinner"></span>
        <span>搜索中...</span>
      </div>
      <div v-else-if="error" class="meme-error">
        <el-alert :title="error" type="warning" show-icon :closable="false" />
      </div>
      <div v-else-if="!list.length" class="meme-empty">
        <el-empty :description="`未找到与「${keyword}」相关的内容`" />
      </div>
      <div v-else class="meme-list-wrap">
        <el-row :gutter="16" class="meme-row">
          <el-col
            v-for="item in list"
            :key="item.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
            class="meme-col"
          >
            <router-link
              :to="{
                name: 'memeDetail',
                params: { id: item.id },
                query: keyword ? { from: 'search', keyword } : { from: 'search' },
              }"
              class="meme-card-link"
            >
              <el-card
                shadow="hover"
                class="meme-card"
                :body-style="{
                  padding: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  height: '100%',
                }"
              >
                <div class="meme-img-wrap">
                  <el-image
                    :src="item.image"
                    :alt="item.name"
                    fit="cover"
                    class="meme-img"
                    lazy
                  >
                    <template #error>
                      <div class="meme-img-error">
                        <span>加载失败</span>
                      </div>
                    </template>
                  </el-image>
                  <div class="meme-img-overlay">
                    <span class="overlay-stat">👁 {{ formatNum(item.pageViews) }}</span>
                    <span class="overlay-stat">💬 {{ formatNum(item.comments) }}</span>
                  </div>
                </div>
                <div class="meme-info">
                  <div class="meme-name" :title="item.name">{{ item.name || '—' }}</div>
                  <div class="meme-stats">
                    <span title="浏览量">👁 {{ formatNum(item.pageViews) }}</span>
                    <span title="点赞">👍 {{ formatNum(item.likes) }}</span>
                    <span title="评论">💬 {{ formatNum(item.comments) }}</span>
                  </div>
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
                  <div class="meme-time">
                    {{ formatDate(item.releaseTime || item.updateTime) || '—' }}
                  </div>
                </div>
              </el-card>
            </router-link>
          </el-col>
        </el-row>
        <!-- 无限滚动触底哨兵 -->
        <div
          v-if="list.length && !noMore"
          ref="sentinelRef"
          class="scroll-sentinel"
          aria-hidden="true"
        />
        <div v-if="list.length && loadMoreLoading" class="meme-loading">
          <span class="meme-loading-spinner"></span>
          <span>加载中...</span>
        </div>
        <div v-else-if="list.length && noMore" class="meme-nomore">
          没有更多了
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { searchMeme } from '@/api/search'
import AppBreadcrumb from '@/components/layout/AppBreadcrumb.vue'
import { buildSearchBreadcrumbs } from '@/utils/pageBreadcrumb'

const SORT_TABS = [
  { label: '综合排序', value: '' },
  { label: '最多点击', value: 'mostViews' },
  { label: '最多点赞', value: 'mostLikes' },
  { label: '最多评论', value: 'mostComments' },
]

const PAGE_SIZE = 8

export default {
  name: 'SearchPage',
  components: {
    AppBreadcrumb,
  },
  data() {
    return {
      sortTabs: SORT_TABS,
      sortBy: '',
      list: [],
      fullList: null,
      page: 1,
      loading: false,
      loadMoreLoading: false,
      error: '',
      observer: null,
    }
  },
  computed: {
    keyword() {
      return (this.$route.query.keyword || '').trim()
    },
    breadcrumbItems() {
      return buildSearchBreadcrumbs({ keyword: this.keyword })
    },
    noMore() {
      if (!this.fullList) return true
      return this.list.length >= this.fullList.length
    },
  },
  watch: {
    keyword: {
      immediate: true,
      handler() {
        this.fetchList()
      },
    },
    sortBy() {
      this.fetchList()
    },
  },
  mounted() {
    this.initObserver()
  },
  beforeUnmount() {
    this.observer?.disconnect()
  },
  methods: {
    initObserver() {
      this.observer = new IntersectionObserver(
        (entries) => {
          const entry = entries[0]
          if (entry?.isIntersecting && this.fullList && !this.noMore && !this.loadMoreLoading) {
            this.loadMore()
          }
        },
        { root: null, rootMargin: '100px', threshold: 0 }
      )
    },
    observeSentinel() {
      this.$nextTick(() => {
        const el = this.$refs.sentinelRef
        if (el) this.observer?.observe(el)
      })
    },
    changeSort(value) {
      this.sortBy = value
    },
    tags(item) {
      return item.memeTag || item.label || []
    },
    async fetchList() {
      if (!this.keyword) {
        this.list = []
        this.fullList = null
        this.page = 1
        this.error = ''
        return
      }
      this.loading = true
      this.error = ''
      this.fullList = null
      this.page = 1
      try {
        const params = {
          keyword: this.keyword,
        }
        if (this.sortBy === 'mostViews') params.mostViews = '1'
        else if (this.sortBy === 'mostLikes') params.mostLikes = '1'
        else if (this.sortBy === 'mostComments') params.mostComments = '1'
        const all = await searchMeme(params)
        this.fullList = all
        this.list = all.slice(0, PAGE_SIZE)
        this.page = 1
        this.observeSentinel()
      } catch (e) {
        this.error = e.message || '搜索失败'
        this.list = []
      } finally {
        this.loading = false
      }
    },
    loadMore() {
      if (!this.fullList || this.noMore || this.loadMoreLoading) return
      this.loadMoreLoading = true
      this.$nextTick(() => {
        this.page += 1
        this.list = this.fullList.slice(0, this.page * PAGE_SIZE)
        this.loadMoreLoading = false
        this.observeSentinel()
      })
    },
    formatNum(num) {
      if (num == null) return '0'
      const n = Number(num)
      if (n >= 1e8) return (n / 1e8).toFixed(1) + '亿'
      if (n >= 1e4) return (n / 1e4).toFixed(1) + '万'
      return String(n)
    },
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
  padding: 24px 32px;
}

.search-page {
  max-width: 1400px;
  margin: 0 auto;
}

/* 排序 tab 栏：类似参考图 */
.search-toolbar {
  margin-bottom: 20px;
}

.sort-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sort-tab {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.sort-tab:hover {
  color: #111827;
  background-color: #f3f4f6;
}

.sort-tab.active {
  color: #318AEF;
  background-color: #e8f2fd;
  font-weight: 600;
}

.search-empty-tip {
  padding: 48px 0;
}

.meme-list-wrap {
  min-height: 120px;
}

.scroll-sentinel {
  height: 1px;
  width: 100%;
  pointer-events: none;
  visibility: hidden;
}

.meme-nomore {
  text-align: center;
  padding: 24px;
  color: #6b7280;
  font-size: 14px;
}

.meme-row {
  margin: 0 -8px;
}

.meme-col {
  margin-bottom: 16px;
}

.meme-card {
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  transition: box-shadow 0.2s ease, transform 0.15s ease, border-color 0.2s ease;
  display: flex;
  flex-direction: column;
  height: 320px;
}

.meme-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 0;
}

.meme-card:hover {
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  transform: translateY(-3px);
  border-color: #d1d5db;
}

.meme-card-link {
  display: block;
  text-decoration: none !important;
  color: inherit;
}

.meme-card-link:hover,
.meme-card-link:visited,
.meme-card-link:focus {
  text-decoration: none !important;
}

.meme-card-link * {
  text-decoration: none !important;
}

.meme-img-wrap {
  position: relative;
  width: 100%;
  height: 160px;
  flex-shrink: 0;
  background: #f3f4f6;
  overflow: hidden;
}

.meme-img {
  width: 100%;
  height: 100%;
  display: block;
}

.meme-img-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 8px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #fff;
}

.overlay-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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

.meme-info {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.meme-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meme-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}

.meme-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
  min-height: 26px;
}

.meme-tag {
  margin: 0;
}

.meme-time {
  font-size: 12px;
  color: #9ca3af;
  margin-top: auto;
  text-decoration: none;
}

.meme-loading,
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
  border-top-color: #318AEF;
  border-radius: 50%;
  animation: search-spin 0.8s linear infinite;
}

@keyframes search-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
