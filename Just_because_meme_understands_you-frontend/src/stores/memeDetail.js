/**
 * 梗详情页状态：与接口 GET /detail?memeId= 对应，集中管理详情数据与加载状态
 */
import { defineStore } from 'pinia'
import { getMemeDetail, getMemeFavoriteStatus } from '@/api/meme'
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
        this.error = '缺少梗 id，无法加载详情'
        this.errorCode = undefined
        return
      }

      this.loading = true
      this.loadingMemeId = id
      this.error = ''
      this.errorCode = undefined
      this.isFavorited = false
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
        if (authStore.isLoggedIn && !preview) {
          const status = await getMemeFavoriteStatus(id).catch(() => ({ favorited: false }))
          favorited = !!(status && status.favorited)
        }

        this.meme = data || null
        this.isFavorited = favorited
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
      } finally {
        if (this.loadingMemeId === id) {
          this.loading = false
        }
      }
    },

    setFavorited(value) {
      this.isFavorited = !!value
    },

    /** 清空详情状态（如离开页面时可选调用） */
    clearDetail() {
      this.meme = null
      this.loadingMemeId = ''
      this.isFavorited = false
      this.loading = false
      this.error = ''
      this.errorCode = undefined
    },
  },
})
