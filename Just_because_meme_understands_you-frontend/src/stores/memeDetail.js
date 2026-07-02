/**
 * 梗详情页状态：与接口 GET /detail?memeId= 对应，集中管理详情数据与加载状态
 */
import { defineStore } from 'pinia'
import { getMemeDetail } from '@/api/meme'

export const useMemeDetailStore = defineStore('memeDetail', {
  state: () => ({
    /** 梗详情对象，与接口 GET /detail 返回的 data 一致 */
    meme: null,
    loading: false,
    /** @type {string} */
    error: '',
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
  },

  actions: {
    /**
     * 根据 memeId 拉取梗详情并更新 state
     * @param {string|number} memeId
     */
    async fetchDetail(memeId) {
      const id = memeId != null ? String(memeId).trim() : ''
      if (!id) {
        this.meme = null
        this.error = '缺少梗 id，无法加载详情'
        return
      }
      this.loading = true
      this.error = ''
      try {
        const data = await getMemeDetail(id)
        this.meme = data || null
      } catch (e) {
        this.error = e.message || '加载梗详情失败'
        this.meme = null
      } finally {
        this.loading = false
      }
    },

    /** 清空详情状态（如离开页面时可选调用） */
    clearDetail() {
      this.meme = null
      this.loading = false
      this.error = ''
    },
  },
})
