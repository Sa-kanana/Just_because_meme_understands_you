<template>
  <div class="page search-page">
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
      <div v-if="fromHome" class="search-entry">
        <h2 class="search-entry__title">搜梗</h2>
        <p class="search-entry__desc">在顶部搜索框输入关键词，查找梗图名称、介绍或标签。</p>
        <ul class="search-entry__tips">
          <li>支持标签名搜索，例如从首页热门标签点选会自动带入</li>
          <li>可使用排序筛选：最多点击 / 点赞 / 评论</li>
        </ul>
      </div>
      <ui-empty v-else description="请输入关键字进行搜索" />
    </div>
    <template v-else>
      <div v-if="loading" class="meme-loading">
        <span class="meme-loading-spinner"></span>
        <span>搜索中...</span>
      </div>
      <div v-else-if="error" class="meme-error">
        <vs-alert :title="error" color="warn" />
      </div>
      <div v-else-if="!list.length" class="meme-empty">
        <ui-empty :description="`未找到与「${keyword}」相关的内容`" />
      </div>
      <div v-else class="meme-list-wrap">
        <div class="meme-grid">
          <MemeCard
            v-for="item in list"
            :key="item.id"
            class="meme-grid__item"
            :to="memeDetailLocation(item.id)"
            :name="item.name"
            :image="item.image"
            :page-views="item.pageViews"
            :likes="item.likes"
            :comments="item.comments"
            :release-time="item.releaseTime"
            :update-time="item.updateTime"
            :author="item.author"
          />
        </div>
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
import MemeCard from '@/components/meme/MemeCard.vue'
import { buildMemeDetailLocation } from '@/utils/pageBreadcrumb'

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
    MemeCard,
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
    fromHome() {
      return String(this.$route.query.from || '').trim() === 'home'
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
    memeDetailLocation(id) {
      return buildMemeDetailLocation(id, {
        from: 'search',
        keyword: this.keyword,
      })
    },
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
  color: var(--meme-text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.sort-tab:hover {
  color: var(--meme-text);
  background-color: var(--meme-bg-muted);
}

.sort-tab.active {
  color: var(--meme-primary);
  background-color: var(--meme-primary-soft);
  font-weight: 600;
}

.search-empty-tip {
  padding: 48px 0;
}

.search-entry {
  max-width: 560px;
  margin: 0 auto;
  padding: 28px 24px;
  border-radius: 16px;
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-gradient-card);
}

.search-entry__title {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  color: var(--meme-text);
}

.search-entry__desc {
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--meme-text-secondary);
}

.search-entry__tips {
  margin: 0;
  padding-left: 18px;
  color: var(--meme-text-secondary);
  font-size: 13px;
  line-height: 1.8;
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
  color: var(--meme-text-secondary);
  font-size: 14px;
}

.meme-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.meme-grid__item {
  min-width: 0;
}

.meme-loading,
.meme-error,
.meme-empty {
  text-align: center;
  padding: 24px;
  color: var(--meme-text-secondary);
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
  border: 2px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: search-spin 0.8s linear infinite;
}

@keyframes search-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 960px) {
  .meme-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .meme-grid {
    gap: 10px;
  }
}
</style>
