<template>
  <div class="notifications-page">
    <el-card class="notifications-card" shadow="never">
      <header class="notifications-head">
        <div class="notifications-head__copy">
          <h1 class="notifications-title">消息中心</h1>
          <p class="notifications-subtitle">互动提醒与系统通知都会在这里汇总</p>
        </div>
        <el-dropdown trigger="click" @command="handleBatchCommand">
          <el-button class="notifications-manage-btn">
            管理
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="readAll">全部已读</el-dropdown-item>
              <el-dropdown-item command="clearRead">清空已读</el-dropdown-item>
              <el-dropdown-item command="clearAll" divided>清空全部</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <el-tabs v-model="activeTab" class="notifications-tabs" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane name="unread">
          <template #label>
            <span class="tab-label">
              未读
              <el-badge
                v-if="notificationStore.unreadTotal > 0"
                :value="notificationStore.unreadTotal"
                :max="99"
                class="tab-badge"
              />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane label="互动" name="interact" />
        <el-tab-pane label="系统" name="system" />
      </el-tabs>

      <div v-if="loading && !list.length" class="notifications-loading">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="!list.length" class="notifications-empty">
        <el-empty description="暂无消息" :image-size="80" />
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
        <el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  components: { ArrowDown, NotificationListItem },
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
        ElMessage.error(e.message || '加载消息失败')
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
        ElMessage.error(e.message || '加载更多失败')
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
        await ElMessageBox.confirm('确认删除这条消息？', '删除消息', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        })
      } catch (_) {
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
        ElMessage.success('已删除')
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '删除失败')
      }
    },
    async handleBatchCommand(command) {
      if (command === 'readAll') {
        await this.markAllRead()
        return
      }
      const isClearAll = command === 'clearAll'
      const isClearRead = command === 'clearRead'
      if (!isClearAll && !isClearRead) return
      const title = isClearAll ? '清空全部消息' : '清空已读消息'
      const message = isClearAll
        ? '确认清空全部消息？此操作不可恢复。'
        : '确认清空所有已读消息？'
      try {
        await ElMessageBox.confirm(message, title, {
          type: 'warning',
          confirmButtonText: '确认',
          cancelButtonText: '取消',
        })
      } catch (_) {
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
        ElMessage.success(`已删除 ${data.deletedCount} 条`)
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '操作失败')
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
        ElMessage.success(
          data.updatedCount > 0 ? `已标记 ${data.updatedCount} 条为已读` : '没有未读消息'
        )
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        ElMessage.error(e.message || '全部已读失败')
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
  padding: 24px 32px 40px;
  max-width: 960px;
  margin: 0 auto;
}

.notifications-card {
  border-radius: var(--meme-radius-lg);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
}

.notifications-card :deep(.el-card__body) {
  padding: 22px 24px 20px;
}

.notifications-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}

.notifications-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.01em;
  color: var(--meme-text);
}

.notifications-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--meme-text-secondary);
}

.notifications-manage-btn {
  border-radius: 10px;
}

.notifications-tabs {
  margin-top: 8px;
}

.notifications-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.notifications-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--meme-border);
}

.notifications-tabs :deep(.el-tabs__item) {
  font-weight: 600;
  color: var(--meme-text-secondary);
}

.notifications-tabs :deep(.el-tabs__item.is-active) {
  color: var(--meme-primary);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  transform: translateY(-1px);
}

.notifications-loading,
.notifications-empty {
  padding: 28px 0 16px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 4px;
}

.notifications-load-more {
  margin-top: 18px;
  padding-top: 4px;
  text-align: center;
}

@media (max-width: 768px) {
  .notifications-page {
    padding: 16px;
  }

  .notifications-card :deep(.el-card__body) {
    padding: 18px 16px 16px;
  }

  .notifications-head {
    flex-wrap: wrap;
  }
}
</style>
