<template>
  <article
    class="notification-item"
    :class="{ 'is-unread': !item.isRead }"
    @click="$emit('open', item)"
  >
    <el-avatar
      :size="44"
      :src="item.actor?.avatar || ''"
      class="notification-item__avatar"
      @click.stop="$emit('profile', item)"
    >
      {{ avatarFallback }}
    </el-avatar>

    <div class="notification-item__body">
      <div class="notification-item__row">
        <div class="notification-item__copy">
          <strong class="notification-item__title">
            <button
              v-if="actorName"
              type="button"
              class="notification-item__actor"
              @click.stop="$emit('profile', item)"
            >
              {{ actorName }}
            </button>
            <span>{{ actionText }}</span>
          </strong>
          <p v-if="item.content" class="notification-item__content">{{ item.content }}</p>
        </div>
        <div class="notification-item__meta">
          <time class="notification-item__time" :datetime="item.createTime">{{ timeText }}</time>
          <button
            type="button"
            class="notification-item__delete"
            aria-label="删除消息"
            @click.stop="$emit('delete', item)"
          >
            删除
          </button>
        </div>
      </div>

      <div
        v-if="item.extra?.memeCover || item.extra?.memeName"
        class="notification-item__meme"
      >
        <el-image
          v-if="item.extra.memeCover"
          :src="item.extra.memeCover"
          fit="cover"
          class="notification-item__cover"
        />
        <span v-if="item.extra.memeName" class="notification-item__meme-name">
          {{ item.extra.memeName }}
        </span>
      </div>
    </div>

    <span v-if="!item.isRead" class="notification-item__dot" aria-label="未读" />
  </article>
</template>

<script>
const TYPE_ACTIONS = {
  like: '点赞了你的梗',
  comment: '评论了你的梗',
  reply: '回复了你的评论',
  favorite: '收藏了你的梗',
  follow: '关注了你',
  comment_like: '赞了你的评论',
  system: '系统通知',
  audit: '审核通知',
  review: '审核通知',
  announcement: '公告',
}

export default {
  name: 'NotificationListItem',
  props: {
    item: {
      type: Object,
      required: true,
    },
    timeText: {
      type: String,
      default: '',
    },
  },
  emits: ['open', 'delete', 'profile'],
  computed: {
    avatarFallback() {
      const name = this.item?.actor?.nickname || this.item?.title || '消'
      return String(name).trim().slice(0, 1) || '消'
    },
    actorName() {
      const name = this.item?.actor?.nickname
      return name != null ? String(name).trim() : ''
    },
    actionText() {
      const type = String(this.item?.type || '').trim().toLowerCase()
      const fromType = TYPE_ACTIONS[type]
      if (fromType) return fromType
      const title = String(this.item?.title || '').trim()
      // 标题里若已带昵称，去掉开头昵称，避免「昵称 昵称 评论了你的梗」
      if (this.actorName && title.startsWith(this.actorName)) {
        return title.slice(this.actorName.length).trim() || title
      }
      return title || '发来一条消息'
    },
  },
}
</script>

<style scoped>
.notification-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 14px;
  margin: 0 -6px;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.notification-item:hover {
  background: var(--meme-bg-muted);
}

.notification-item.is-unread {
  background: color-mix(in srgb, var(--meme-primary-soft) 70%, transparent);
}

.notification-item.is-unread::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 18px;
  bottom: 18px;
  width: 3px;
  border-radius: 2px;
  background: var(--meme-primary);
}

.notification-item__avatar {
  flex-shrink: 0;
  cursor: pointer;
}

.notification-item__body {
  flex: 1;
  min-width: 0;
}

.notification-item__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.notification-item__copy {
  min-width: 0;
  flex: 1;
}

.notification-item__title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--meme-text);
}

.notification-item__actor {
  padding: 0;
  margin: 0 4px 0 0;
  border: none;
  background: transparent;
  color: var(--meme-text);
  font: inherit;
  font-weight: 800;
  cursor: pointer;
  vertical-align: baseline;
}

.notification-item__actor:hover {
  color: var(--meme-primary);
}

.notification-item__content {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.notification-item__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding-top: 1px;
}

.notification-item__time {
  font-size: 12px;
  color: var(--meme-text-muted);
  white-space: nowrap;
}

.notification-item__delete {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--meme-danger);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.notification-item:hover .notification-item__delete,
.notification-item:focus-within .notification-item__delete {
  opacity: 0.85;
}

.notification-item__delete:hover {
  opacity: 1 !important;
}

.notification-item__meme {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  max-width: 100%;
  padding: 6px 10px 6px 6px;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
}

.notification-item__cover {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.notification-item__meme-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item__dot {
  position: absolute;
  top: 18px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--meme-primary);
  display: none;
}

@media (max-width: 640px) {
  .notification-item__row {
    flex-direction: column;
    gap: 6px;
  }

  .notification-item__meta {
    width: 100%;
    justify-content: space-between;
  }

  .notification-item__delete {
    opacity: 0.85;
  }

  .notification-item.is-unread::before {
    display: none;
  }

  .notification-item__dot {
    display: block;
  }
}
</style>
