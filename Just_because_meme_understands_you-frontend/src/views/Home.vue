<template>
  <div class="page home-page">
    <HomeHeroBanner
      @explore="scrollToFeed"
      @publish="goPublish"
    />

    <!-- 快捷入口 -->
    <HomeQuickActions
      v-if="quickActions.length"
      :actions="quickActions"
      @action="handleQuickAction"
    />

    <!-- 热门标签 -->
    <HomeHotTags
      v-if="hotTags.length"
      :tags="hotTags"
      @select="handleTagSelect"
    />

    <!-- 今日热梗横滑 -->
    <HomeHotMemeRail v-if="hotMemes.length" :memes="hotMemes" />

    <!-- Feed 列表 -->
    <section ref="feedSection" class="meme-section">
      <div class="meme-section-head">
        <div>
          <p class="meme-section-kicker">FEED</p>
          <h2 class="meme-section-title">发现梗图</h2>
        </div>
      </div>
      <HomeFeedTabs
        v-model="feedSort"
        :is-logged-in="isLoggedIn"
      />

      <div v-if="bootstrapLoading && !memeList.length" class="meme-loading">
        <span class="meme-loading-spinner"></span>
        <span>加载中...</span>
      </div>

      <div v-else-if="bootstrapError && !memeList.length" class="meme-error">
        <vs-alert :title="bootstrapError" color="warn" />
        <vs-button color="primary" transparent class="meme-retry-btn" @click="loadBootstrap">重试</vs-button>
      </div>

      <div v-else class="meme-list-wrap">
        <div class="meme-grid">
          <MemeCard
            v-for="item in memeList"
            :key="item.id"
            class="meme-grid__item"
            :to="memeDetailFromFeed(item.id)"
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

        <ListLoadFooter
          :has-more="feedHasMore"
          :loading="feedLoading"
          :item-count="memeList.length"
          @load-more="loadMoreFeed"
        />

        <div v-if="feedError" class="meme-error">
          <vs-alert :title="feedError" color="warn" />
        </div>

        <div v-if="!memeList.length && !feedLoading && !bootstrapLoading" class="meme-empty">
          <ui-empty :description="emptyFeedText" />
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { getMemeList } from '@/api/meme'
import { getHomeBootstrap } from '@/api/home'
import MemeCard from '@/components/meme/MemeCard.vue'
import HomeHeroBanner from '@/components/home/HomeHeroBanner.vue'
import HomeQuickActions from '@/components/home/HomeQuickActions.vue'
import HomeHotTags from '@/components/home/HomeHotTags.vue'
import HomeHotMemeRail from '@/components/home/HomeHotMemeRail.vue'
import HomeFeedTabs from '@/components/home/HomeFeedTabs.vue'
import ListLoadFooter from '@/components/layout/ListLoadFooter.vue'
import { useAuthStore } from '@/stores/auth'
import { resolvePageHasMore } from '@/utils/pagination'
import { sanitizeExternalUrl } from '@/utils/safeUrl'
import { pushQuickActionTarget, resolveQuickActionTarget } from '@/utils/homeQuickAction'
import {
  buildMemeDetailLocation,
  buildSearchLocation,
  buildToolPageLocation,
} from '@/utils/pageBreadcrumb'

const DEFAULT_QUICK_ACTIONS = [
  { key: 'publish', label: '发布梗', route: '/publish', requireLogin: true },
  { key: 'ai', label: 'AI 搜梗', route: '/ai', requireLogin: true },
  { key: 'search', label: '搜梗', route: '/search', requireLogin: false },
  { key: 'favorites', label: '我的收藏', route: '/user/me?tab=favorite', requireLogin: true },
]

export default {
  name: 'HomePage',
  components: {
    MemeCard,
    HomeHeroBanner,
    HomeQuickActions,
    HomeHotTags,
    HomeHotMemeRail,
    HomeFeedTabs,
    ListLoadFooter,
  },
  data() {
    return {
      quickActions: [...DEFAULT_QUICK_ACTIONS],
      hotTags: [],
      hotMemes: [],
      memeList: [],
      feedSort: 'hot',
      feedPage: 1,
      feedPageSize: 16,
      feedHasMore: false,
      bootstrapLoading: false,
      feedLoading: false,
      bootstrapError: '',
      feedError: '',
    }
  },
  computed: {
    authStore() {
      return useAuthStore()
    },
    isLoggedIn() {
      return this.authStore.isLoggedIn
    },
    currentUserId() {
      const user = this.authStore.currentUser
      if (!user || typeof user !== 'object') return ''
      if (user.id != null) return String(user.id).trim()
      if (user.userId != null) return String(user.userId).trim()
      return ''
    },
    emptyFeedText() {
      if (this.feedSort === 'following') {
        return this.isLoggedIn ? '你还没有关注任何人，或关注的人暂未发布梗图' : '登录后可查看关注动态'
      }
      return '暂无梗图数据'
    },
  },
  watch: {
    feedSort() {
      this.reloadFeed()
    },
  },
  mounted() {
    this.loadBootstrap()
  },
  methods: {
    scrollToFeed() {
      const el = this.$refs.feedSection
      if (el && typeof el.scrollIntoView === 'function') {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },
    memeDetailFromFeed(id) {
      return buildMemeDetailLocation(id, { from: 'feed' })
    },
    goPublish() {
      if (!this.isLoggedIn) {
        this.$router.push({
          name: 'login',
          query: { redirect: '/publish' },
        })
        return
      }
      this.$router.push(buildToolPageLocation('publishMeme', { from: 'home' }))
    },
    async loadBootstrap() {
      this.bootstrapLoading = true
      this.bootstrapError = ''
      try {
        const data = await getHomeBootstrap({
          hotLimit: 12,
          tagLimit: 12,
          feedSort: this.feedSort,
          feedSize: this.feedPageSize,
        })
        if (Array.isArray(data.quickActions) && data.quickActions.length) {
          const fromApi = data.quickActions
          const hasAi = fromApi.some((a) => a && String(a.key || '') === 'ai')
          this.quickActions = hasAi
            ? fromApi
            : [
                fromApi[0],
                { key: 'ai', label: 'AI 搜梗', route: '/ai', requireLogin: true },
                ...fromApi.slice(1),
              ].filter(Boolean)
        }
        this.hotTags = data.hotTags || []
        this.hotMemes = data.hotMemes || []
        const feed = data.feed || {}
        this.memeList = feed.list || []
        this.feedPage = feed.page || 1
        this.feedPageSize = feed.size || this.feedPageSize
        this.feedHasMore = resolvePageHasMore(
          feed,
          (feed.list || []).length,
          this.feedPageSize,
          this.memeList.length,
        )
      } catch (e) {
        this.bootstrapError = e.message || '加载首页数据失败'
      } finally {
        this.bootstrapLoading = false
      }
    },
    async reloadFeed() {
      if (this.feedSort === 'following' && !this.isLoggedIn) {
        this.memeList = []
        this.feedHasMore = false
        this.feedError = ''
        return
      }
      this.feedLoading = true
      this.feedError = ''
      this.feedPage = 1
      this.memeList = []
      try {
        const data = await getMemeList({
          page: 1,
          sort: this.feedSort,
          size: this.feedPageSize,
        })
        this.memeList = data.list || []
        this.feedPage = data.page || 1
        this.feedPageSize = data.size || this.feedPageSize
        this.feedHasMore = resolvePageHasMore(
          data,
          (data.list || []).length,
          this.feedPageSize,
          this.memeList.length,
        )
      } catch (e) {
        this.feedError = e.message || '加载梗图列表失败'
        this.memeList = []
        this.feedHasMore = false
      } finally {
        this.feedLoading = false
      }
    },
    async loadMoreFeed() {
      if (this.feedLoading || !this.feedHasMore || this.feedError) return
      if (this.feedSort === 'following' && !this.isLoggedIn) return
      this.feedLoading = true
      this.feedError = ''
      try {
        const nextPage = this.feedPage + 1
        const data = await getMemeList({
          page: nextPage,
          sort: this.feedSort,
          size: this.feedPageSize,
        })
        const existingIds = new Set(this.memeList.map((item) => item.id))
        const newItems = (data.list || []).filter((item) => !existingIds.has(item.id))
        this.memeList = this.memeList.concat(newItems)
        this.feedPage = data.page || nextPage
        this.feedHasMore = resolvePageHasMore(
          data,
          (data.list || []).length,
          this.feedPageSize,
          this.memeList.length,
        )
      } catch (e) {
        this.feedError = e.message || '加载更多失败'
      } finally {
        this.feedLoading = false
      }
    },
    handleQuickAction(action) {
      if (!action) return

      const ctx = {
        isLoggedIn: this.isLoggedIn,
        currentUserId: this.currentUserId,
      }

      if (action.requireLogin && !ctx.isLoggedIn) {
        const target = resolveQuickActionTarget(action, ctx)
        const redirect = target?.kind === 'login'
          ? target.redirect
          : this.$route.fullPath
        this.$router.push({
          name: 'login',
          query: { redirect },
        })
        return
      }

      const target = resolveQuickActionTarget(action, ctx)
      if (!target) return

      if (target.kind === 'external') {
        const safeUrl = sanitizeExternalUrl(target.url)
        if (safeUrl) window.open(safeUrl, '_blank', 'noopener,noreferrer')
        return
      }

      pushQuickActionTarget(this.$router, target)
    },
    handleTagSelect(tag) {
      const name = tag && tag.name ? String(tag.name).trim() : ''
      if (!name) return
      this.$router.push(buildSearchLocation({ keyword: name, from: 'home' }))
    },
  },
}
</script>

<style scoped>
.page {
  padding: 0 0 20px;
  overflow-x: hidden;
}

.meme-section {
  margin-top: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.meme-list-wrap {
  min-height: 120px;
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

.meme-retry-btn {
  margin-top: 12px;
}

.meme-loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: meme-spin 0.8s linear infinite;
}

@keyframes meme-spin {
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
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
}

@media (max-width: 768px) {
  .page {
    padding: 0 0 20px;
  }
}
</style>
