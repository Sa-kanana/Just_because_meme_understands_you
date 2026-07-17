import { defineStore } from 'pinia'
import { getNotificationUnreadCount } from '@/api/notification'
import { isAuthErrorHandled } from '@/utils/authSession'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadTotal: 0,
    unreadInteract: 0,
    unreadSystem: 0,
    loading: false,
    _refreshPromise: null,
  }),

  getters: {
    hasUnread: (state) => state.unreadTotal > 0,
  },

  actions: {
    setUnreadCount(payload = {}) {
      this.unreadTotal = Number.isFinite(Number(payload.total)) ? Number(payload.total) : 0
      this.unreadInteract = Number.isFinite(Number(payload.interact)) ? Number(payload.interact) : 0
      this.unreadSystem = Number.isFinite(Number(payload.system)) ? Number(payload.system) : 0
    },

    reset() {
      this.unreadTotal = 0
      this.unreadInteract = 0
      this.unreadSystem = 0
      this.loading = false
      this._refreshPromise = null
    },

    async refreshUnreadCount() {
      if (this._refreshPromise) return this._refreshPromise
      this.loading = true
      this._refreshPromise = getNotificationUnreadCount()
        .then((data) => {
          this.setUnreadCount(data)
          return data
        })
        .catch((e) => {
          if (isAuthErrorHandled(e)) return null
          throw e
        })
        .finally(() => {
          this.loading = false
          this._refreshPromise = null
        })
      return this._refreshPromise
    },
  },
})
