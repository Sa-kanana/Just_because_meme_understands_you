/**
 * 全局面包屑动态补丁（详情名、用户昵称、加载态等）
 * 路由 name/params 变化时由 router.beforeEach 清空，页面按需写入。
 */
import { defineStore } from 'pinia'
import {
  recordBreadcrumbVisit,
  updateCurrentBreadcrumbVisit,
} from '@/utils/breadcrumbTrail.mjs'

export const useBreadcrumbStore = defineStore('breadcrumb', {
  state: () => ({
    /** @type {Record<string, any>} */
    patch: {},
    /** 当前会话按路由访问顺序记录的面包屑路径。 */
    trail: [],
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
    recordVisit(route, currentItem) {
      this.trail = recordBreadcrumbVisit(this.trail, route, currentItem)
    },
    updateCurrentVisit(route, currentItem) {
      this.trail = updateCurrentBreadcrumbVisit(this.trail, route, currentItem)
    },
  },
})
