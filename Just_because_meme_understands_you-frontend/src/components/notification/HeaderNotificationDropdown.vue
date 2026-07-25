<template>
  <ui-dropdown
    trigger="hover"
    placement="bottom-end"
    @command="handleCommand"
  >
    <ui-badge
      :is-dot="hasUnread"
      :hidden="!hasUnread"
      class="header-notification-badge"
    >
      <button
        type="button"
        class="header-notification-btn"
        aria-label="消息"
        title="消息"
      >
        <i class="ri-notification-3-line header-notification-icon" aria-hidden="true" />
      </button>
    </ui-badge>
    <template #dropdown>
      <ui-dropdown-menu class="notification-dropdown-menu">
        <ui-dropdown-item command="all">
          <i class="ri-notification-3-line notification-dropdown-icon" />
          <span>消息中心</span>
        </ui-dropdown-item>
        <ui-dropdown-item command="unread">
          <i class="ri-mail-unread-line notification-dropdown-icon" />
          <span>未读消息</span>
          <ui-badge
            v-if="unreadTotal > 0"
            :value="unreadTotal"
            :max="99"
            class="notification-dropdown-badge"
          />
        </ui-dropdown-item>
        <ui-dropdown-item command="interact">
          <i class="ri-heart-3-line notification-dropdown-icon" />
          <span>互动消息</span>
        </ui-dropdown-item>
        <ui-dropdown-item command="system">
          <i class="ri-megaphone-line notification-dropdown-icon" />
          <span>系统通知</span>
        </ui-dropdown-item>
        <ui-dropdown-item
          divided
          command="readAll"
          :disabled="unreadTotal <= 0 || markingAll"
        >
          <i class="ri-check-double-line notification-dropdown-icon" />
          <span>{{ markingAll ? '处理中…' : '全部已读' }}</span>
        </ui-dropdown-item>
      </ui-dropdown-menu>
    </template>
  </ui-dropdown>
</template>

<script>
import { toast } from '@/utils/uiFeedback'
import { markAllNotificationsRead } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import { isAuthErrorHandled } from '@/utils/authSession'
import { buildToolPageLocation } from '@/utils/pageBreadcrumb'

export default {
  name: 'HeaderNotificationDropdown',
  data() {
    return {
      markingAll: false,
    }
  },
  computed: {
    notificationStore() {
      return useNotificationStore()
    },
    hasUnread() {
      return this.notificationStore.hasUnread
    },
    unreadTotal() {
      return this.notificationStore.unreadTotal
    },
  },
  methods: {
    handleCommand(command) {
      if (command === 'readAll') {
        this.markAllRead()
        return
      }
      this.goNotifications(command)
    },
    goNotifications(tab = 'all') {
      const patch = useBreadcrumbStore().patch || {}
      const location = buildToolPageLocation('notifications', {
        fromRoute: this.$route,
        memeName: patch.meme?.name || '',
        profileName: patch.nickname || '',
      })
      const nextTab = ['all', 'unread', 'interact', 'system'].includes(tab) ? tab : 'all'
      if (nextTab !== 'all') {
        location.query = {
          ...(location.query || {}),
          tab: nextTab,
        }
      }
      this.$router.push(location)
    },
    async markAllRead() {
      if (this.markingAll || this.unreadTotal <= 0) return
      this.markingAll = true
      try {
        const data = await markAllNotificationsRead({ tab: 'all' })
        this.notificationStore.setUnreadCount({
          total: data.unreadCount,
          interact: data.interact,
          system: data.system,
        })
        toast.success(
          data.updatedCount > 0 ? `已标记 ${data.updatedCount} 条为已读` : '没有未读消息'
        )
      } catch (e) {
        if (isAuthErrorHandled(e)) return
        toast.error(e.message || '全部已读失败')
      } finally {
        this.markingAll = false
      }
    },
  },
}
</script>

<style scoped>
.header-notification-badge {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.header-notification-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  margin: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--meme-text-secondary);
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.15s ease;
}

.header-notification-icon {
  font-size: 22px;
}

.header-notification-btn:hover {
  color: var(--meme-primary);
  background-color: var(--meme-bg-muted);
}
</style>

<style>
.notification-dropdown-menu {
  min-width: 168px;
}

.notification-dropdown-menu .ui-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.notification-dropdown-menu .ui-dropdown-item:hover:not(:disabled) {
  background-color: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.notification-dropdown-icon {
  font-size: 16px;
}

.notification-dropdown-badge {
  margin-left: auto;
}
</style>
