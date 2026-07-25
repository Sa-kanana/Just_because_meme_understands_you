<template>
  <div class="notifications-page">
    <header class="notifications-hero">
      <p class="notifications-hero-kicker">INBOX</p>
      <h1 class="notifications-hero-title">消息中心</h1>
      <p class="notifications-hero-sub">互动提醒与系统通知都会在这里汇总</p>
    </header>

    <vs-card class="notifications-card">
      <template #text>
        <div class="notifications-toolbar">
          <ui-tabs v-model="activeTab" class="notifications-tabs" @tab-change="onTabChange">
            <ui-tab-pane label="全部" name="all" />
            <ui-tab-pane :label="unreadTabLabel" name="unread" />
            <ui-tab-pane label="互动" name="interact" />
            <ui-tab-pane label="系统" name="system" />
          </ui-tabs>

          <div class="notifications-actions">
            <vs-button
              type="border"
              color="primary"
              class="notifications-readall-btn"
              @click="markAllRead"
            >
              <i class="ri-check-double-line" aria-hidden="true" />
              全部已读
            </vs-button>
            <ui-dropdown trigger="click" @command="handleBatchCommand">
              <vs-button type="border" color="primary" class="notifications-manage-btn">
                <i class="ri-settings-3-line" aria-hidden="true" />
                管理
                <i class="ri-arrow-down-s-line notifications-manage-icon" aria-hidden="true" />
              </vs-button>
              <template #dropdown>
                <ui-dropdown-menu>
                  <ui-dropdown-item command="clearRead">
                    <i class="ri-eraser-line" aria-hidden="true" />
                    清空已读
                  </ui-dropdown-item>
                  <ui-dropdown-item command="clearAll" divided>
                    <i class="ri-delete-bin-line" aria-hidden="true" />
                    清空全部
                  </ui-dropdown-item>
                </ui-dropdown-menu>
              </template>
            </ui-dropdown>
          </div>
        </div>

        <div v-if="loading && !list.length" class="notifications-loading">
          <ui-skeleton :rows="4" animated />
        </div>
        <div v-else-if="!list.length" class="notifications-empty">
          <div class="notifications-empty__icon" aria-hidden="true">
            <i class="ri-inbox-2-line" />
          </div>
          <p class="notifications-empty__title">暂无消息</p>
          <p class="notifications-empty__desc">有人点赞、评论或系统通知时，会显示在这里</p>
        </div>
        <div v-else class="notification-list">
          <NotificationListItem
            v-for="item in list"
            :key="item.id"
            :item="item"
            :time-text="formatTime(item.createTime)"
            @open="openNotification"
            @delete="removeOne"
            @profile="goActorProfile"
          />
        </div>

        <div v-if="hasMore" class="notifications-load-more">
          <vs-button
            class="meme-load-more-btn"
            type="border"
            color="primary"
            :loading="loadingMore"
            @click="loadMore"
          >
            <i v-if="!loadingMore" class="ri-arrow-down-s-line" aria-hidden="true" />
            加载更多
          </vs-button>
        </div>
      </template>
    </vs-card>
  </div>
</template>

<script>
import { toast, confirmBox } from '@/utils/uiFeedback'
import {
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  deleteNotification,
  batchDeleteNotifications,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useAuthStore } from '@/stores/auth'
import { isAuthErrorHandled } from '@/utils/authSession'
import { buildMemeDetailLocation, buildUserProfileLocation } from '@/utils/pageBreadcrumb'
import NotificationListItem from '@/components/notification/NotificationListItem.vue'

export default {
  name: 'NotificationsPage',
  components: { NotificationListItem },
  data() {
    return {
      activeTab: 'all',
      list: [],
      page: 1,
      size: 10,
      total: 0,
      hasMore: false,
      loading: false,
      loadingMore: false,
    }
  },
  computed: {
    notificationStore() {
      return useNotificationStore()
    },
    unreadTabLabel() {
      const n = this.notificationStore.unreadTotal
      if (n <= 0) return '未读'
      return n > 99 ? '未读 99+' : `未读 ${n}`
    },
  },
  watch: {
    '$route.query.tab'(val) {
      const next = this.normalizeTab(val)
      if (next === this.activeTab) return
      this.activeTab = next
      this.reloadList()
    },
  },
  created() {
    this.activeTab = this.normalizeTab(this.$route.query.tab)
  },
  mounted() {
    this.reloadList()
  },
  methods: {
    normalizeTab(raw) {
      const tab = String(raw || '').trim().toLowerCase()
      return ['all', 'unread', 'interact', 'system'].includes(tab) ? tab : 'all'
    },
    formatTime(str) {
      if (!str) return ''
      const raw = String(str).trim().replace('T', ' ')
      const parsed = new Date(raw.replace(/-/g, '/'))
      if (Number.isNaN(parsed.getTime())) return raw
      const now = Date.now()
      const diff = Math.max(0, now - parsed.getTime())
      const minute = 60 * 1000
      const hour = 60 * minute
      const day = 24 * hour
      if (diff < minute) return '刚刚'
      if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
      if (diff < day) return `${Math.floor(diff / hour)} 小时前`
      if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
      const y = parsed.getFullYear()
      const m = String(parsed.getMonth() + 1).padStart(2, '0')
      const d = String(parsed.getDate()).padStart(2, '0')
      const hh = String(parsed.getHours()).padStart(2, '0')
      const mm = String(parsed.getMinutes()).padStart(2, '0')
      if (y === new Date().getFullYear()) return `${m}-${d} ${hh}:${mm}`
      return `${y}-${m}-${d}`
    },
    onTabChange(tab) {
      const next = this.normalizeTab(tab)
      this.activeTab = next
      const current = this.normalizeTab(this.$route.query.tab)
      if (current !== next) {
        const query = { ...this.$route.query }
        if (next === 'all') {
          delete query.tab
        } else {
          query.tab = next
        }
        this.$router.replace({ query }).catch(() => {})
      }
      this.reloadList()
    },
    async reloadList() {
      this.page = 1
      this.loading = true
      try {
        const data = await getNotifications({
          page: 1,
          size: this.size,
          tab: this.activeTab === 'all' ? '' : this.activeTab,
        })
        this.list = data.list
        this.page = data.page
        this.total = data.total
        this.hasMore = data.hasMore
        await this.notificationStore.refreshUnreadCount()
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '加载消息失败')
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      if (!this.hasMore || this.loadingMore) return
      this.loadingMore = true
      try {
        const nextPage = this.page + 1
        const data = await getNotifications({
          page: nextPage,
          size: this.size,
          tab: this.activeTab === 'all' ? '' : this.activeTab,
        })
        this.list = this.list.concat(data.list)
        this.page = data.page
        this.total = data.total
        this.hasMore = data.hasMore
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '加载更多失败')
      } finally {
        this.loadingMore = false
      }
    },
    goActorProfile(item) {
      const userId = item?.actor?.userId
      if (!userId) return
      this.$router.push(
        buildUserProfileLocation(userId, {
          fromRoute: this.$route,
        })
      )
    },
    async openNotification(item) {
      if (!item?.id) return
      if (!item.isRead) {
        try {
          const data = await markNotificationRead(item.id)
          item.isRead = true
          this.notificationStore.setUnreadCount({
            total: data.unreadCount,
            interact: this.notificationStore.unreadInteract,
            system: this.notificationStore.unreadSystem,
          })
          await this.notificationStore.refreshUnreadCount()
        } catch (e) {
          if (isAuthErrorHandled(e)) return
        }
      }
      const jump = item.jump
      if (!jump?.name) return
      if (jump.name === 'memeDetail') {
        const memeId = jump.params?.id || item.targetId
        if (!memeId) return
        const location = buildMemeDetailLocation(memeId, { from: 'header' })
        const commentId = jump.query?.commentId || item.refId
        const rootId = jump.query?.rootId
        if (commentId) {
          location.query = {
            ...location.query,
            commentId: String(commentId),
          }
          if (rootId) {
            location.query.rootId = String(rootId)
          }
        }
        this.$router.push(location)
        return
      }
      if (jump.name === 'userProfile') {
        const userId = jump.params?.userId || item.actor?.userId
        if (!userId) return
        this.$router.push(
          buildUserProfileLocation(userId, {
            fromRoute: this.$route,
          })
        )
      }
    },
    async removeOne(item) {
      if (!item?.id) return
      try {
        await confirmBox('确认删除这条消息？', '删除消息', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        })
      } catch (e) {
        if (e === 'cancel') return
        return
      }
      try {
        const data = await deleteNotification(item.id)
        this.list = this.list.filter((row) => row.id !== item.id)
        this.total = Math.max(0, this.total - 1)
        this.notificationStore.setUnreadCount({
          total: data.unreadCount,
          interact: this.notificationStore.unreadInteract,
          system: this.notificationStore.unreadSystem,
        })
        await this.notificationStore.refreshUnreadCount()
        toast.success('已删除')
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '删除失败')
      }
    },
    async handleBatchCommand(command) {
      const isClearAll = command === 'clearAll'
      const isClearRead = command === 'clearRead'
      if (!isClearAll && !isClearRead) return
      const title = isClearAll ? '清空全部消息' : '清空已读消息'
      const message = isClearAll
        ? '确认清空全部消息？此操作不可恢复。'
        : '确认清空所有已读消息？'
      try {
        await confirmBox(message, title, {
          type: 'warning',
          confirmButtonText: '确认',
          cancelButtonText: '取消',
        })
      } catch (e) {
        if (e === 'cancel') return
        return
      }
      try {
        const data = await batchDeleteNotifications(
          isClearAll ? { clearAll: true } : { clearReadOnly: true }
        )
        await this.reloadList()
        this.notificationStore.setUnreadCount({
          total: data.unreadCount,
          interact: this.notificationStore.unreadInteract,
          system: this.notificationStore.unreadSystem,
        })
        await this.notificationStore.refreshUnreadCount()
        toast.success(`已删除 ${data.deletedCount} 条`)
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '操作失败')
      }
    },
    async markAllRead() {
      const tab =
        this.activeTab === 'interact' || this.activeTab === 'system'
          ? this.activeTab
          : 'all'
      try {
        const data = await markAllNotificationsRead({ tab })
        this.list = this.list.map((row) => ({ ...row, isRead: true }))
        this.notificationStore.setUnreadCount({
          total: data.unreadCount,
          interact: data.interact,
          system: data.system,
        })
        if (this.activeTab === 'unread') {
          await this.reloadList()
        }
        toast.success(
          data.updatedCount > 0 ? `已标记 ${data.updatedCount} 条为已读` : '没有未读消息'
        )
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '全部已读失败')
      }
    },
  },
  beforeRouteLeave() {
    if (!useAuthStore().isLoggedIn) {
      useNotificationStore().reset()
    }
  },
}
</script>

<style scoped>
.notifications-page {
  padding: 8px 0 36px;
  max-width: 880px;
  margin: 0 auto;
}

.notifications-hero {
  margin-bottom: 18px;
}

.notifications-hero-kicker {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--meme-primary);
}

.notifications-hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--meme-text);
}

.notifications-hero-sub {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
}

.notifications-card {
  border-radius: 16px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.notifications-card :deep(.vs-card__text) {
  padding: 18px 20px 20px;
}

.notifications-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.notifications-tabs {
  flex: 1;
  min-width: 0;
}

.notifications-tabs :deep(.ui-tabs__nav) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  margin-bottom: 0;
  border: 1px solid var(--meme-border);
  border-radius: 12px;
  background: var(--meme-bg-muted);
  width: fit-content;
  max-width: 100%;
  flex-wrap: wrap;
}

.notifications-tabs :deep(.ui-tabs__item) {
  padding: 8px 14px;
  border: none;
  border-radius: 9px;
  border-bottom: none;
  margin-bottom: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-text-secondary);
  background: transparent;
  box-shadow: none !important;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.notifications-tabs :deep(.ui-tabs__item:hover) {
  color: var(--meme-text);
  background: color-mix(in srgb, var(--meme-bg-elevated) 70%, transparent);
}

.notifications-tabs :deep(.ui-tabs__item.is-active) {
  background: var(--meme-bg-elevated) !important;
  color: var(--meme-primary) !important;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08) !important;
  font-weight: 700;
}

.notifications-tabs :deep(.ui-tabs__body) {
  display: none;
}

.notifications-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.notifications-readall-btn,
.notifications-manage-btn {
  height: 36px !important;
  padding: 0 14px !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  font-weight: 650 !important;
  color: var(--meme-primary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 40%, var(--meme-border)) !important;
  box-shadow: none !important;
}

.notifications-readall-btn:hover:not(:disabled),
.notifications-manage-btn:hover:not(:disabled) {
  background: var(--meme-primary-soft) !important;
  color: var(--meme-primary-dark) !important;
  filter: none !important;
}

.notifications-readall-btn :deep(.vs-button__content),
.notifications-readall-btn :deep(.vs-button__content *),
.notifications-manage-btn :deep(.vs-button__content),
.notifications-manage-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: inherit !important;
}

.notifications-manage-icon {
  font-size: 14px;
  margin-left: 0;
}

.notifications-loading {
  padding: 24px 0 12px;
}

.notifications-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 16px 48px;
  text-align: center;
}

.notifications-empty__icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-size: 30px;
  margin-bottom: 14px;
}

.notifications-empty__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--meme-text);
}

.notifications-empty__desc {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--meme-text-muted);
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 8px;
}

.notifications-load-more {
  margin-top: 18px;
  padding-top: 4px;
  text-align: center;
}

@media (max-width: 768px) {
  .notifications-page {
    padding: 4px 0 28px;
  }

  .notifications-hero-title {
    font-size: 24px;
  }

  .notifications-card :deep(.vs-card__text) {
    padding: 14px 14px 16px;
  }

  .notifications-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .notifications-tabs :deep(.ui-tabs__nav) {
    width: 100%;
  }

  .notifications-actions {
    width: 100%;
  }

  .notifications-readall-btn,
  .notifications-manage-btn {
    flex: 1;
  }
}
</style>
