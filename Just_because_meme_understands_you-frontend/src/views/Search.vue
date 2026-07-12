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
      <div v-if="fromHome" class="search-entry">
        <h2 class="search-entry__title">搜梗</h2>
        <p class="search-entry__desc">在顶部搜索框输入关键词，查找梗图名称、介绍或标签。</p>
        <ul class="search-entry__tips">
          <li>支持标签名搜索，例如从首页热门标签点选会自动带入</li>
          <li>可使用排序筛选：最多点击 / 点赞 / 评论</li>
        </ul>
      </div>
      <el-empty v-else description="请输入关键字进行搜索" />
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
            <MemeCard
              :to="{
                name: 'memeDetail',
                params: { id: item.id },
                query: keyword ? { from: 'search', keyword } : { from: 'search' },
              }"
              :name="item.name"
              :image="item.image"
              :page-views="item.pageViews"
              :likes="item.likes"
              :comments="item.comments"
              :release-time="item.releaseTime"
              :update-time="item.updateTime"
            />
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
import MemeCard from '@/components/meme/MemeCard.vue'
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

.search-entry {
  max-width: 560px;
  margin: 0 auto;
  padding: 28px 24px;
  border-radius: 16px;
  border: 1px solid rgba(49, 138, 239, 0.14);
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.search-entry__title {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.search-entry__desc {
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
}

.search-entry__tips {
  margin: 0;
  padding-left: 18px;
  color: #64748b;
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
  color: #6b7280;
  font-size: 14px;
}

.meme-row {
  margin: 0 -8px;
}

.meme-col {
  margin-bottom: 20px;
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
