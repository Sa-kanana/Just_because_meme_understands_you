<template>
  <div class="notifications-page">
    <el-card class="notifications-card" shadow="never">
      <div class="notifications-head">
        <div class="notifications-head-copy">
          <h1 class="notifications-title">消息中心</h1>
          <p class="notifications-subtitle">互动提醒与系统通知都会在这里汇总</p>
        </div>
        <div class="notifications-head-actions">
          <el-button
            v-if="activeTab !== 'all'"
            link
            @click="activeTab = 'all'"
          >
            查看全部
          </el-button>
          <el-dropdown trigger="click" @command="handleBatchCommand">
            <el-button :disabled="!list.length && !loading">
              管理
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="clearRead">清空已读</el-dropdown-item>
                <el-dropdown-item command="clearAll" divided>清空全部</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="notifications-tabs" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane name="unread">
          <template #label>
            <span>未读</span>
            <el-badge
              v-if="notificationStore.unreadTotal > 0"
              :value="notificationStore.unreadTotal"
              class="tab-badge"
            />
          </template>
        </el-tab-pane>
        <el-tab-pane label="互动" name="interact" />
        <el-tab-pane label="系统" name="system" />
      </el-tabs>

      <div v-if="loading && !list.length" class="notifications-loading">
        <el-skeleton :rows="4" animated />
      </div>
      <el-empty
        v-else-if="!list.length"
        description="暂无消息"
        :image-size="72"
      />
      <div v-else class="notification-list">
        <article
          v-for="item in list"
          :key="item.id"
          class="notification-item"
          :class="{ 'is-unread': !item.isRead }"
          @click="openNotification(item)"
        >
          <el-avatar
            :size="42"
            :src="item.actor?.avatar || ''"
            class="notification-avatar"
          >
            {{ avatarFallback(item) }}
          </el-avatar>
          <div class="notification-main">
            <div class="notification-item-head">
              <strong class="notification-title">{{ item.title || typeLabel(item.type) }}</strong>
              <time class="notification-time">{{ formatTime(item.createTime) }}</time>
            </div>
            <p v-if="item.content" class="notification-content">{{ item.content }}</p>
            <div v-if="item.extra?.memeCover || item.extra?.memeName" class="notification-extra">
              <el-image
                v-if="item.extra.memeCover"
                :src="item.extra.memeCover"
                fit="cover"
                class="notification-cover"
              />
              <span v-if="item.extra.memeName" class="notification-meme-name">{{ item.extra.memeName }}</span>
            </div>
          </div>
          <div class="notification-side">
            <span v-if="!item.isRead" class="notification-dot" aria-label="未读" />
            <el-button
              link
              type="danger"
              class="notification-delete"
              @click.stop="removeOne(item)"
            >
              删除
            </el-button>
          </div>
        </article>
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
  deleteNotification,
  batchDeleteNotifications,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useAuthStore } from '@/stores/auth'
import { isAuthErrorHandled } from '@/utils/authSession'
import { buildMemeDetailLocation, buildUserProfileLocation } from '@/utils/pageBreadcrumb'

const TYPE_LABELS = {
  like: '点赞',
  comment: '评论',
  reply: '回复',
  favorite: '收藏',
  follow: '关注',
  comment_like: '评论点赞',
  system: '系统通知',
  audit: '审核通知',
  review: '审核通知',
  announcement: '公告',
}

export default {
  name: 'NotificationsPage',
  components: { ArrowDown },
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
  mounted() {
    this.reloadList()
  },
  methods: {
    typeLabel(type) {
      const key = String(type || '').trim().toLowerCase()
      return TYPE_LABELS[key] || '消息'
    },
    avatarFallback(item) {
      const name = item?.actor?.nickname || item?.title || '消'
      return String(name).trim().slice(0, 1) || '消'
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
    onTabChange() {
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
        const commentId = jump.query?.commentId
        if (commentId) {
          location.query = { ...location.query, commentId: String(commentId) }
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
  padding: 24px 32px;
}

.notifications-card {
  border-radius: 16px;
  border: 1px solid var(--meme-border);
}

.notifications-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.notifications-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--meme-text);
}

.notifications-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--meme-text-secondary);
}

.notifications-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.notifications-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.tab-badge {
  margin-left: 6px;
}

.notifications-loading {
  padding: 12px 0;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.notification-item:hover {
  background: var(--meme-bg-muted);
}

.notification-item.is-unread {
  background: var(--meme-primary-soft);
}

.notification-avatar {
  flex-shrink: 0;
}

.notification-main {
  flex: 1;
  min-width: 0;
}

.notification-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}

.notification-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--meme-text);
}

.notification-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.notification-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.notification-extra {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.notification-cover {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  border: 1px solid var(--meme-border);
  overflow: hidden;
}

.notification-meme-name {
  font-size: 13px;
  color: var(--meme-text);
  font-weight: 600;
}

.notification-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.notification-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--meme-primary);
}

.notification-delete {
  opacity: 0;
  transition: opacity 0.15s ease;
}

.notification-item:hover .notification-delete {
  opacity: 1;
}

.notifications-load-more {
  margin-top: 16px;
  text-align: center;
}

@media (max-width: 768px) {
  .notifications-page {
    padding: 16px;
  }

  .notifications-head {
    flex-direction: column;
  }
}
</style>
