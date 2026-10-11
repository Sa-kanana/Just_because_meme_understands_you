import { defineStore } from 'pinia'
import {
  applyUserSuppression,
  getMySuppressions,
  removeUserSuppression,
} from '@/api/userSafety'
import { buildSafetyState, isSuppressedUser, normalizeUserId } from '@/utils/userSafety.mjs'

export const useUserSafetyStore = defineStore('userSafety', {
  state: () => ({
    loadedForUserId: '',
    loading: false,
    items: [],
    mutedUserIds: [],
    blockedUserIds: [],
  }),
  getters: {
    isLoaded: (state) => !!state.loadedForUserId && !state.loading,
  },
  actions: {
    async load(userId) {
      const id = normalizeUserId(userId)
      if (!id) {
        this.reset()
        return
      }
      if (this.loadedForUserId === id) return
      this.items = []
      this.mutedUserIds = []
      this.blockedUserIds = []
      this.loadedForUserId = id
      this.loading = true
      try {
        const rows = await getMySuppressions()
        if (this.loadedForUserId !== id) return
        this.items = Array.isArray(rows) ? rows : []
        this.applyRows(id)
      } catch (error) {
        if (this.loadedForUserId === id) this.loadedForUserId = ''
        throw error
      } finally {
        if (this.loadedForUserId === id) this.loading = false
      }
    },
    reset() {
      this.loadedForUserId = ''
      this.loading = false
      this.items = []
      this.mutedUserIds = []
      this.blockedUserIds = []
    },
    applyRows(userId = this.loadedForUserId) {
      const state = buildSafetyState(this.items, userId, !!userId)
      this.mutedUserIds = [...state.mutedUserIds]
      this.blockedUserIds = [...state.blockedUserIds]
    },
    isHidden(userId) {
      return isSuppressedUser({
        viewerId: this.loadedForUserId,
        authenticated: !!this.loadedForUserId,
        mutedUserIds: new Set(this.mutedUserIds),
        blockedUserIds: new Set(this.blockedUserIds),
      }, userId)
    },
    async apply(targetUserId, type) {
      const targetId = normalizeUserId(targetUserId)
      const ownerId = this.loadedForUserId
      await applyUserSuppression(targetId, type)
      if (!ownerId || this.loadedForUserId !== ownerId) return
      const direction = 'OUTGOING'
      const index = this.items.findIndex((item) => item.type === type && item.direction !== 'INCOMING'
        && normalizeUserId(item.targetUserId) === targetId)
      if (index < 0) this.items.push({ targetUserId: targetId, type, direction })
      this.applyRows()
    },
    async remove(targetUserId, type) {
      const targetId = normalizeUserId(targetUserId)
      const ownerId = this.loadedForUserId
      await removeUserSuppression(targetId, type)
      if (!ownerId || this.loadedForUserId !== ownerId) return
      this.items = this.items.filter((item) => !(item.type === type && item.direction !== 'INCOMING'
        && normalizeUserId(item.targetUserId) === targetId))
      this.applyRows()
    },
  },
})
