<template>
  <div class="page home-page">
    <HomeHeroBanner
      @explore="scrollToFeed"
      @publish="goPublish"
    />

    <!-- 热门标签紧跟首页轮播，保持进入首页后容易发现 -->
    <HomeHotTags
      v-if="hotTags.length"
      :tags="hotTags"
      :followed-tag-ids="followedTagIds"
      :busy-tag-ids="busyTagIds"
      @select="handleTagSelect"
      @toggle-follow="toggleTagFollow"
    />
    <div v-if="tagFollowError" class="tag-follow-error" role="alert">
      <span class="tag-follow-error__message">
        <span class="tag-follow-error__dot" aria-hidden="true"></span>
        关注标签暂时无法同步，请检查后端服务后重试。
      </span>
      <vs-button type="flat" color="primary" @click="loadFollowedTags">重试同步</vs-button>
    </div>

    <!-- 快捷入口放在梗图列表之前，方便快速进入常用功能 -->
    <HomeQuickActions
      v-if="quickActions.length"
      :actions="quickActions"
      @action="handleQuickAction"
    />

    <div class="home-content" :class="{ 'home-content--single': !hotMemes.length }">
      <!-- Feed 列表 -->
      <section ref="feedSection" class="meme-section">
        <div class="meme-section-head">
          <div>
            <h2 class="meme-section-title">发现梗图</h2>
          </div>
        </div>
        <HomeFeedTabs
          v-model="feedSort"
          :is-logged-in="isLoggedIn"
          :disabled="feedSwitching"
        />

        <div v-if="(bootstrapLoading || feedLoading) && !memeList.length" class="meme-loading">
          <span class="meme-loading-spinner"></span>
          <span>加载中...</span>
        </div>

        <div v-else-if="bootstrapError && !memeList.length" class="feed-offline-state" role="alert" aria-live="assertive">
          <div class="feed-offline-state__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M2.5 8.8a15 15 0 0 1 15.9-3.4M5.8 12a9.8 9.8 0 0 1 7.4-2.1M9.2 15.3a4.7 4.7 0 0 1 2.4-.6M3 3l18 18" />
            </svg>
          </div>
          <div class="feed-offline-state__copy">
            <h3>服务器暂未连接</h3>
            <p>请稍后重试，或确认服务已启动。</p>
          </div>
          <vs-button
            color="primary"
            class="feed-offline-state__retry"
            :disabled="bootstrapLoading"
            @click="loadBootstrap"
          >
            {{ bootstrapLoading ? '正在重试…' : '重新连接' }}
          </vs-button>
        </div>

        <div
          v-else
          class="meme-list-wrap"
          :class="{ 'is-switching': feedSwitching }"
          :aria-busy="feedSwitching"
        >
          <div class="meme-grid">
            <MemeCard
              v-for="(item, index) in memeList"
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
              stats-placement="body"
              :lazy="index >= 3"
            />
          </div>

          <div v-if="feedError" class="feed-error-banner" role="alert" aria-live="polite">
            <span class="feed-error-banner__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M2.5 8.8a15 15 0 0 1 15.9-3.4M5.8 12a9.8 9.8 0 0 1 7.4-2.1M9.2 15.3a4.7 4.7 0 0 1 2.4-.6M3 3l18 18" />
              </svg>
            </span>
            <span class="feed-error-banner__copy">
              <strong>新梗图暂时没有加载出来</strong>
              <span>请确认后端服务正常运行，然后再试一次。</span>
            </span>
            <vs-button
              color="primary"
              type="border"
              class="feed-error-banner__retry"
              :disabled="feedLoading"
              @click="retryFeedLoad"
            >
              {{ feedLoading ? '正在重试…' : '重试加载' }}
            </vs-button>
          </div>

          <div v-else-if="feedLoading && memeList.length" class="feed-load-status" aria-live="polite">
            <span class="meme-loading-spinner" aria-hidden="true"></span>
            <span>正在加载更多梗图…</span>
          </div>

          <div v-else-if="feedHasMore && memeList.length" class="feed-load-status">
            <vs-button
              v-if="!feedObserverAvailable"
              type="border"
              color="primary"
              :disabled="feedLoading"
              @click="loadNextFeedPage"
            >
              加载更多
            </vs-button>
            <span v-else>继续下滑，自动加载更多</span>
          </div>

          <div v-else-if="memeList.length" class="feed-load-status feed-load-status--end" aria-live="polite">
            已经到底啦
          </div>

          <div v-if="!memeList.length && !feedLoading && !bootstrapLoading && !feedError" class="meme-empty">
            <ui-empty :description="emptyFeedText" />
          </div>
        </div>

        <div ref="feedSentinel" class="feed-load-sentinel" aria-hidden="true"></div>
      </section>
      <aside v-if="hotMemes.length" class="home-content__aside" aria-label="今日热梗">
        <HomeHotMemeRail :memes="hotMemes" />
      </aside>
    </div>

  </div>
</template>

<script>
import { getMemeList } from '@/api/meme'
import { getHomeBootstrap, getFollowedTags, followTag, unfollowTag } from '@/api/home'
import MemeCard from '@/components/meme/MemeCard.vue'
import HomeHeroBanner from '@/components/home/HomeHeroBanner.vue'
import HomeQuickActions from '@/components/home/HomeQuickActions.vue'
import HomeHotTags from '@/components/home/HomeHotTags.vue'
import HomeHotMemeRail from '@/components/home/HomeHotMemeRail.vue'
import HomeFeedTabs from '@/components/home/HomeFeedTabs.vue'
import { useAuthStore } from '@/stores/auth'
import { clampPageToTotal } from '@/utils/pageMetadata.mjs'
import {
  appendUniqueFeedItems,
  hasNextFeedPage,
  shouldReobserveFeedSentinel,
} from '@/utils/feedPagination.mjs'
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
  { key: 'following', label: '我的关注', route: '/?feed=following', requireLogin: true },
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
  },
  data() {
    return {
      quickActions: [...DEFAULT_QUICK_ACTIONS],
      hotTags: [],
      followedTagIds: [],
      busyTagIds: [],
      tagFollowError: '',
      hotMemes: [],
      memeList: [],
      feedSort: 'hot',
      feedPage: 1,
      feedPageSize: 12,
      feedHasMore: false,
      bootstrapLoading: false,
      feedLoading: false,
      feedSwitching: false,
      feedRequestSeq: 0,
      feedObserver: null,
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
      if (this.feedSort === 'tags') {
        return this.followedTagIds.length
          ? '关注的标签暂时没有已发布的梗图'
          : '还没有关注标签，可在上方热门标签中关注喜欢的梗'
      }
      return '暂无梗图数据'
    },
    feedObserverAvailable() {
      return typeof window !== 'undefined' && 'IntersectionObserver' in window
    },
  },
  watch: {
    feedSort(next, prev) {
      if (next === prev) return
      this.reloadFeed({ preserveList: true })
    },
    isLoggedIn(value) {
      if (value) {
        this.loadFollowedTags()
        this.applyFeedQuery()
      }
      else {
        this.followedTagIds = []
      }
    },
    '$route.query.feed': {
      immediate: true,
      handler(feed) {
        this.applyFeedQuery(feed)
      },
    },
  },
  mounted() {
    this.loadBootstrap()
    if (this.isLoggedIn) this.loadFollowedTags()
  },
  beforeUnmount() {
    if (this.feedObserver) {
      this.feedObserver.disconnect()
      this.feedObserver = null
    }
  },
  methods: {
    applyFeedQuery(feed = this.$route.query.feed) {
      const value = feed != null ? String(feed).trim().toLowerCase() : ''
      if (value !== 'following' && value !== 'tags') return
      if (!this.isLoggedIn) return
      if (this.feedSort !== value) {
        this.feedSort = value
      }
      this.$nextTick(() => this.scrollToFeed())
    },
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
          const fromApi = data.quickActions.map((action) => {
            if (action && String(action.key || '') === 'search') {
              return {
                ...action,
                key: 'following',
                label: '我的关注',
                route: '/?feed=following',
                requireLogin: true,
              }
            }
            return action
          })
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
        this.feedHasMore = hasNextFeedPage(
          feed,
          this.memeList.length,
          this.feedPageSize,
          this.feedPage,
        )
        if (this.feedSort === 'tags' || this.feedSort === 'following') {
          await this.reloadFeed()
        }
        else if (!feed.totalAvailable) {
          await this.reloadFeed({ preserveList: true, page: this.feedPage })
        }
      } catch (e) {
        this.bootstrapError = e.message || '加载首页数据失败'
      } finally {
        this.bootstrapLoading = false
        this.scheduleFeedSentinelObservation()
      }
    },
    async reloadFeed({ preserveList = false, page = 1 } = {}) {
      if (this.feedObserver) {
        this.feedObserver.disconnect()
        this.feedObserver = null
      }
      if ((this.feedSort === 'following' || this.feedSort === 'tags') && !this.isLoggedIn) {
        this.memeList = []
        this.feedPage = 1
        this.feedHasMore = false
        this.feedError = ''
        this.feedSwitching = false
        this.feedLoading = false
        return
      }

      const targetPage = Math.max(1, Math.floor(Number(page) || 1))
      const requestId = ++this.feedRequestSeq
      const keepPrevious = preserveList && this.memeList.length > 0
      this.feedLoading = true
      this.feedSwitching = keepPrevious
      this.feedError = ''
      this.feedHasMore = false
      // 保留旧列表撑住高度，避免 Tab 切换时页面塌陷跳动
      if (!keepPrevious) {
        this.memeList = []
      }

      try {
        let data
        let requestPage = targetPage
        for (let attempt = 0; attempt < 3; attempt += 1) {
          data = await getMemeList({
            page: requestPage,
            sort: this.feedSort,
            size: this.feedPageSize,
          })
          if (requestId !== this.feedRequestSeq) return
          const latestTotal = Number(data.total) || 0
          const latestPageSize = Number(data.size) || this.feedPageSize
          const validPage = clampPageToTotal(requestPage, latestTotal, latestPageSize)
          if (latestTotal <= 0 || validPage === requestPage) break
          if (attempt === 2) {
            throw new Error('梗图数据正在变化，请稍后重试')
          }
          requestPage = validPage
        }
        this.memeList = data.list || []
        this.feedPageSize = data.size || this.feedPageSize
        this.feedPage = clampPageToTotal(
          data.page || requestPage,
          data.total,
          this.feedPageSize,
        )
        this.feedHasMore = hasNextFeedPage(
          data,
          this.memeList.length,
          this.feedPageSize,
          this.feedPage,
        )
      } catch (e) {
        if (requestId !== this.feedRequestSeq) return
        this.feedError = e.message || '加载梗图列表失败'
        if (!keepPrevious) {
          this.memeList = []
        }
      } finally {
        if (requestId === this.feedRequestSeq) {
          this.feedLoading = false
          this.feedSwitching = false
          this.scheduleFeedSentinelObservation()
        }
      }
    },
    scheduleFeedSentinelObservation() {
      this.$nextTick(() => {
        if (!shouldReobserveFeedSentinel({
          feedHasMore: this.feedHasMore,
          feedLoading: this.feedLoading,
          feedSwitching: this.feedSwitching,
          feedError: this.feedError,
          itemCount: this.memeList.length,
          observerActive: Boolean(this.feedObserver),
        })) return

        this.observeFeedSentinel()
      })
    },
    observeFeedSentinel() {
      if (!this.feedObserverAvailable) return
      const sentinel = this.$refs.feedSentinel
      if (!sentinel) return
      // 已观察的哨兵保持连接，避免每页结束时重新观察并连续触发加载。
      if (this.feedObserver) return
      this.feedObserver = new IntersectionObserver((entries) => {
        if (!this.feedError && entries.some((entry) => entry.isIntersecting)) {
          this.loadNextFeedPage()
        }
      }, {
        root: null,
        rootMargin: '480px 0px',
        threshold: 0,
      })
      this.feedObserver.observe(sentinel)
    },
    retryFeedLoad() {
      if (this.feedHasMore && this.memeList.length) {
        return this.loadNextFeedPage()
      }
      return this.reloadFeed()
    },
    async loadNextFeedPage() {
      if (this.feedLoading || this.feedSwitching || !this.feedHasMore || !this.memeList.length) return
      if ((this.feedSort === 'following' || this.feedSort === 'tags') && !this.isLoggedIn) return

      const requestId = ++this.feedRequestSeq
      let requestPage = this.feedPage + 1
      this.feedLoading = true
      this.feedError = ''

      try {
        let data
        for (let attempt = 0; attempt < 3; attempt += 1) {
          data = await getMemeList({
            page: requestPage,
            sort: this.feedSort,
            size: this.feedPageSize,
          })
          if (requestId !== this.feedRequestSeq) return
          const latestTotal = Number(data.total) || 0
          const latestPageSize = Number(data.size) || this.feedPageSize
          const validPage = clampPageToTotal(requestPage, latestTotal, latestPageSize)
          if (latestTotal <= 0 || validPage === requestPage) break
          if (attempt === 2) {
            throw new Error('梗图数据正在变化，请稍后重试')
          }
          requestPage = validPage
        }

        const nextItems = data.list || []
        this.memeList = appendUniqueFeedItems(this.memeList, nextItems)
        this.feedPageSize = data.size || this.feedPageSize
        this.feedPage = clampPageToTotal(
          data.page || requestPage,
          data.total,
          this.feedPageSize,
        )
        this.feedHasMore = hasNextFeedPage(
          data,
          nextItems.length,
          this.feedPageSize,
          this.feedPage,
        )
      } catch (e) {
        if (requestId !== this.feedRequestSeq) return
        this.feedError = e.message || '加载更多梗图失败'
      } finally {
        if (requestId === this.feedRequestSeq) {
          this.feedLoading = false
          this.scheduleFeedSentinelObservation()
        }
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

      if (target.kind === 'home-feed') {
        const sort = target.sort != null ? String(target.sort).trim() : ''
        if (sort && this.feedSort !== sort) {
          this.feedSort = sort
        }
        this.$nextTick(() => this.scrollToFeed())
        return
      }

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
    async loadFollowedTags() {
      try {
        const tags = await getFollowedTags()
        this.followedTagIds = tags.map((tag) => Number(tag.id)).filter(Number.isFinite)
        this.tagFollowError = ''
      } catch (error) {
        this.tagFollowError = error.message || '加载关注标签失败'
      }
    },
    async toggleTagFollow(tag) {
      const tagId = Number(tag && tag.id)
      if (!Number.isInteger(tagId) || tagId <= 0 || this.busyTagIds.includes(tagId)) return
      if (!this.isLoggedIn) {
        this.$router.push({ name: 'login', query: { redirect: this.$route.fullPath } })
        return
      }
      const followed = this.followedTagIds.includes(tagId)
      this.busyTagIds = [...this.busyTagIds, tagId]
      this.tagFollowError = ''
      try {
        if (followed) await unfollowTag(tagId)
        else await followTag(tagId)
        this.followedTagIds = followed
          ? this.followedTagIds.filter((id) => id !== tagId)
          : [...this.followedTagIds, tagId]
        if (this.feedSort === 'tags') await this.reloadFeed()
      } catch (error) {
        this.tagFollowError = error.message || '更新关注标签失败'
      } finally {
        this.busyTagIds = this.busyTagIds.filter((id) => id !== tagId)
      }
    },
  },
}
</script>

<style scoped>
.page {
  padding: 0 0 28px;
  overflow-x: hidden;
}

.home-content {
  display: grid;
  grid-template-columns: minmax(0, 2.8fr) minmax(320px, 1fr);
  align-items: start;
  gap: 18px;
}

.home-content--single {
  grid-template-columns: minmax(0, 1fr);
}

.home-content__aside {
  min-width: 0;
}

.meme-section {
  min-width: 0;
  margin-top: 0;
  scroll-margin-top: 84px;
  padding: 20px 22px 24px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-lg);
  background: linear-gradient(
    145deg,
    var(--meme-bg-card) 0%,
    color-mix(in srgb, var(--meme-bg-card) 97%, var(--meme-primary-soft)) 100%
  );
  box-shadow: var(--meme-shadow-soft);
}

.home-content :deep(.meme-card) {
  border-radius: 16px;
  box-shadow: 0 7px 20px rgba(27, 69, 125, 0.055);
}

.home-content :deep(.meme-card__cover) {
  aspect-ratio: 1.72 / 1;
}

.home-content :deep(.meme-card__title) {
  color: var(--meme-text);
}

.home-content :deep(.meme-card--link:hover) {
  box-shadow: var(--meme-shadow-card);
}

.tag-follow-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 16px;
  padding: 8px 12px 8px 15px;
  border: 1px solid rgba(217, 119, 6, 0.18);
  border-left: 3px solid var(--meme-warning);
  border-radius: 12px;
  background: var(--meme-warning-soft);
  color: var(--meme-text-secondary);
  font-size: 13px;
}

.tag-follow-error__message {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.tag-follow-error__dot,
.feed-offline-state__status > span {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--meme-warning);
  box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.12);
}

.meme-list-wrap {
  position: relative;
  min-height: 120px;
  overflow-anchor: none;
  transition: opacity 0.15s ease;
}

.meme-list-wrap.is-switching {
  opacity: 0.55;
  pointer-events: none;
}

.meme-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 14px;
}

.meme-grid__item {
  flex: 0 0 calc((100% - 42px) / 4);
  min-width: 0;
  content-visibility: auto;
  contain-intrinsic-size: auto 312px;
}

.meme-loading,
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

.feed-offline-state {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  box-sizing: border-box;
  width: min(100%, 680px);
  margin: 14px auto 18px;
  padding: 30px 28px 25px;
  text-align: center;
}

.feed-offline-state__icon {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border: 0;
  color: var(--meme-warning);
}

.feed-offline-state__icon svg {
  width: 27px;
  height: 27px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.feed-offline-state__copy {
  display: grid;
  justify-items: center;
  gap: 7px;
}

.feed-offline-state__status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--meme-warning);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.feed-offline-state__status > span {
  width: 6px;
  height: 6px;
}

.feed-offline-state__copy h3 {
  margin: 0;
  color: var(--meme-text);
  font-size: 20px;
  font-weight: 750;
  line-height: 1.4;
}

.feed-offline-state__copy p {
  max-width: 460px;
  margin: 0;
  color: var(--meme-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.feed-offline-state__retry {
  min-width: 136px;
  margin-top: 2px;
}

.feed-offline-state__hint {
  color: var(--meme-text-muted);
  font-size: 12px;
}

.feed-error-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 14px 0 4px;
  padding: 12px 14px;
  border: 1px solid rgba(217, 119, 6, 0.18);
  border-radius: 15px;
  background: linear-gradient(110deg, var(--meme-warning-soft), var(--meme-bg-card) 70%);
}

.feed-error-banner__icon {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: var(--meme-bg-card);
  color: var(--meme-warning);
}

.feed-error-banner__icon svg {
  width: 20px;
  height: 20px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.feed-error-banner__copy {
  display: grid;
  min-width: 0;
  gap: 3px;
  color: var(--meme-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.feed-error-banner__copy strong {
  color: var(--meme-text);
  font-size: 13px;
}

.feed-error-banner__retry {
  flex: 0 0 auto;
  margin-left: auto;
}

.feed-load-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 52px;
  padding: 8px 0 2px;
  color: var(--meme-text-muted);
  font-size: 13px;
}

.feed-load-status--end {
  color: var(--meme-text-secondary);
}

.feed-load-sentinel {
  width: 100%;
  height: 1px;
  pointer-events: none;
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

@media (max-width: 1180px) {
  .home-content {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-content__aside {
    grid-row: 1;
  }

  .home-content:not(.home-content--single) .meme-section {
    grid-row: 2;
  }

  .meme-grid__item {
    flex-basis: calc((100% - 28px) / 3);
  }
}

@media (max-width: 860px) {
  .meme-grid__item {
    flex-basis: calc((100% - 14px) / 2);
  }
}

@media (max-width: 900px) {
  .meme-section {
    scroll-margin-top: 124px;
  }
}

@media (max-width: 520px) {
  .meme-section {
    scroll-margin-top: 156px;
    padding: 16px 12px 20px;
  }

  .meme-grid {
    gap: 10px;
  }

  .meme-grid__item {
    flex-basis: calc((100% - 10px) / 2);
    contain-intrinsic-size: auto 200px;
  }

  .tag-follow-error {
    align-items: flex-start;
    padding-left: 11px;
    font-size: 12px;
  }

  .feed-offline-state {
    padding: 24px 17px 20px;
  }

  .feed-offline-state__copy h3 {
    font-size: 18px;
  }

  .feed-offline-state__copy p {
    font-size: 13px;
  }

  .feed-error-banner {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .feed-error-banner__retry {
    margin-left: 50px;
  }
}

@media (max-width: 768px) {
  .page {
    padding: 0 0 20px;
  }
}
</style>
