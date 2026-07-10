/**
 * 梗详情页状态：与接口 GET /detail?memeId= 对应，集中管理详情数据与加载状态
 */
import { defineStore } from 'pinia'
import { getMemeDetail, getMemeFavoriteStatus, getMemeLikeStatus } from '@/api/meme'
import { useAuthStore } from './auth'

function isNotFoundLikeError(error) {
  if (!error) return false
  const code = error.code != null ? Number(error.code) : error.status
  if (code === 404 || code === 0) return true
  const msg = String(error.message || '')
  return msg.includes('不存在') || msg.includes('找不到')
}

export const useMemeDetailStore = defineStore('memeDetail', {
  state: () => ({
    /** 梗详情对象，与接口 GET /detail 返回的 data 一致 */
    meme: null,
    /** 当前用户是否已收藏该梗（未登录时为 false） */
    isFavorited: false,
    /** 当前用户是否已点赞该梗（未登录时为 false） */
    isLiked: false,
    loading: false,
    /** 当前正在请求的 memeId，用于避免面包屑/内容串页 */
    loadingMemeId: '',
    /** @type {string} */
    error: '',
    /** @type {number|undefined} */
    errorCode: undefined,
  }),

  getters: {
    /** 标签列表：兼容 memeTag / label */
    tags(state) {
      if (!state.meme) return []
      return state.meme.memeTag || state.meme.label || []
    },
    /** 相关链接列表 */
    links(state) {
      if (!state.meme || !Array.isArray(state.meme.links)) return []
      return state.meme.links
    },
    isOwnerPreview(state) {
      const meme = state.meme
      if (!meme) return false
      if (meme.viewMode === 'owner_preview') return true
      return !!(meme.ownerPreview ?? meme.owner_preview)
    },
  },

  actions: {
    /**
     * 根据 memeId 拉取梗详情并更新 state
     * @param {string|number} memeId
     * @param {{ retried?: boolean }} [options]
     */
    async fetchDetail(memeId, options = {}) {
      const id = memeId != null ? String(memeId).trim() : ''
      if (!id) {
        this.meme = null
        this.loadingMemeId = ''
        this.isFavorited = false
        this.isLiked = false
        this.error = '缺少梗 id，无法加载详情'
        this.errorCode = undefined
        return
      }

      this.loading = true
      this.loadingMemeId = id
      this.error = ''
      this.errorCode = undefined
      this.isFavorited = false
      this.isLiked = false
      this.meme = null

      try {
        const authStore = useAuthStore()
        const data = await getMemeDetail(id)
        if (this.loadingMemeId !== id) return

        const preview = !!(data && (
          data.viewMode === 'owner_preview'
          || data.ownerPreview
          || data.owner_preview
        ))

        let favorited = false
        let liked = false
        if (authStore.isLoggedIn && !preview) {
          const [favoriteStatus, likeStatus] = await Promise.all([
            getMemeFavoriteStatus(id).catch(() => ({ favorited: false })),
            getMemeLikeStatus(id).catch(() => ({ liked: false })),
          ])
          favorited = !!(favoriteStatus && favoriteStatus.favorited)
          liked = !!(likeStatus && likeStatus.liked)
          if (data && likeStatus?.likeCount != null) {
            data.likes = likeStatus.likeCount
          }
        }

        this.meme = data || null
        this.isFavorited = favorited
        this.isLiked = liked
      } catch (e) {
        if (this.loadingMemeId !== id) return

        const authStore = useAuthStore()
        if (!options.retried && authStore.isLoggedIn && isNotFoundLikeError(e)) {
          await new Promise((resolve) => setTimeout(resolve, 120))
          return this.fetchDetail(id, { retried: true })
        }

        this.error = e.message || '加载梗详情失败'
        this.errorCode = e.code != null ? Number(e.code) : e.status
        this.meme = null
        this.isFavorited = false
        this.isLiked = false
      } finally {
        if (this.loadingMemeId === id) {
          this.loading = false
        }
      }
    },

    setFavorited(value) {
      this.isFavorited = !!value
    },

    setLiked(value) {
      this.isLiked = !!value
    },

    /**
     * 仅更新点赞数，避免替换 meme 引用导致评论区等依赖误刷新。
     */
    setLikeCount(count) {
      if (!this.meme) return
      this.meme.likes = Math.max(0, Number(count) || 0)
    },

    /**
     * 仅更新浏览量，避免替换 meme 引用导致评论区等依赖误刷新。
     */
    setPageViews(count) {
      if (!this.meme) return
      this.meme.pageViews = Math.max(0, Number(count) || 0)
    },

    /** 原子更新点赞态与计数（点赞/取消点赞成功后调用） */
    applyLikeState({ liked, likeCount } = {}) {
      if (liked != null) {
        this.isLiked = !!liked
      }
      if (likeCount != null) {
        this.setLikeCount(likeCount)
      }
    },

    /** 清空详情状态（如离开页面时可选调用） */
    clearDetail() {
      this.meme = null
      this.loadingMemeId = ''
      this.isFavorited = false
      this.isLiked = false
      this.loading = false
      this.error = ''
      this.errorCode = undefined
    },
  },
})
