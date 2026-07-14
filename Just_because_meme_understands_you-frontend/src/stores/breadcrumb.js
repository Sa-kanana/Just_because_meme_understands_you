/**
 * 全局面包屑动态补丁（详情名、用户昵称、加载态等）
 * 路由 name/params 变化时由 router.beforeEach 清空，页面按需写入。
 */
import { defineStore } from 'pinia'

export const useBreadcrumbStore = defineStore('breadcrumb', {
  state: () => ({
    /** @type {Record<string, any>} */
    patch: {},
  }),
  actions: {
    setPatch(partial = {}) {
      if (!partial || typeof partial !== 'object') return
      this.patch = {
        ...this.patch,
        ...partial,
      }
    },
    clearPatch() {
      this.patch = {}
    },
  },
})
