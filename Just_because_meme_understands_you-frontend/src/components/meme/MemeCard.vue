<template>
  <component
    :is="rootTag"
    class="meme-card"
    :class="{ 'meme-card--link': isLink }"
    v-bind="rootProps"
    @click="handleClick"
  >
    <div class="meme-card__cover">
      <div class="meme-card__media">
        <ui-image
          v-if="image && !imageError"
          :src="image"
          :alt="displayName"
          fit="cover"
          class="meme-card__img"
          :lazy="lazy"
          @error="imageError = true"
        />
        <MemeCoverPlaceholder
          v-else
          :name="displayName"
          :seed="displayName"
          abstract
          class="meme-card__ph"
        />
      </div>

      <div v-if="showStats" class="meme-card__shade" aria-hidden="true" />
      <div v-if="showStats" class="meme-card__stats">
        <MemeCardStats
          variant="overlay"
          spread
          :page-views="pageViews"
          :likes="likes"
          :comments="comments"
        />
      </div>
      <slot name="cover-extra" />
    </div>

    <div class="meme-card__body">
      <div class="meme-card__title-row">
        <h3 class="meme-card__title" :title="displayName">{{ displayName }}</h3>
        <slot name="title-extra" />
      </div>
      <slot name="footer">
        <div v-if="hasAuthor" class="meme-card__author" @click.stop.prevent="goAuthor">
          <ui-avatar
            :size="20"
            :src="authorAvatar"
            :fallback="authorFallback"
            class="meme-card__author-avatar"
          />
          <span class="meme-card__author-name">{{ authorNickname }}</span>
        </div>
        <p v-else-if="footerText" class="meme-card__meta">{{ footerText }}</p>
      </slot>
    </div>
  </component>
</template>

<script>
import MemeCardStats from '@/components/meme/MemeCardStats.vue'
import MemeCoverPlaceholder from '@/components/meme/MemeCoverPlaceholder.vue'
import { formatDateShort } from '@/utils/formatDate'
import { buildUserProfileLocation } from '@/utils/pageBreadcrumb'

export default {
  name: 'MemeCard',
  components: {
    MemeCardStats,
    MemeCoverPlaceholder,
  },
  props: {
    to: {
      type: [Object, String],
      default: null,
    },
    name: {
      type: String,
      default: '',
    },
    image: {
      type: String,
      default: '',
    },
    pageViews: {
      type: [Number, String],
      default: 0,
    },
    likes: {
      type: [Number, String],
      default: 0,
    },
    comments: {
      type: [Number, String],
      default: 0,
    },
    releaseTime: {
      type: String,
      default: '',
    },
    updateTime: {
      type: String,
      default: '',
    },
    /** 作者信息 { userId, nickname, avatar } */
    author: {
      type: Object,
      default: null,
    },
    showAuthor: {
      type: Boolean,
      default: true,
    },
    /** 覆盖底部默认日期文案 */
    metaText: {
      type: String,
      default: '',
    },
    showStats: {
      type: Boolean,
      default: true,
    },
    lazy: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['click'],
  data() {
    return {
      imageError: false,
    }
  },
  computed: {
    isLink() {
      return this.to != null && this.to !== ''
    },
    rootTag() {
      return this.isLink ? 'router-link' : 'div'
    },
    rootProps() {
      return this.isLink ? { to: this.to } : {}
    },
    displayName() {
      const text = this.name != null ? String(this.name).trim() : ''
      return text || '未命名梗'
    },
    hasAuthor() {
      if (!this.showAuthor || !this.author || typeof this.author !== 'object') return false
      const id = this.author.userId != null ? String(this.author.userId).trim() : ''
      return !!id
    },
    authorNickname() {
      const name = this.author?.nickname != null ? String(this.author.nickname).trim() : ''
      return name || '匿名用户'
    },
    authorAvatar() {
      return this.author?.avatar != null ? String(this.author.avatar).trim() : ''
    },
    authorFallback() {
      return this.authorNickname.charAt(0).toUpperCase()
    },
    footerText() {
      if (this.metaText) return this.metaText
      return formatDateShort(this.releaseTime || this.updateTime)
    },
  },
  watch: {
    image() {
      this.imageError = false
    },
  },
  methods: {
    handleClick(event) {
      this.$emit('click', event)
    },
    goAuthor() {
      const userId = this.author?.userId != null ? String(this.author.userId).trim() : ''
      if (!userId || !/^\d+$/.test(userId)) return
      this.$router.push(
        buildUserProfileLocation(userId, {
          fromRoute: this.$route,
        })
      )
    },
  },
}
</script>

<style scoped>
.meme-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  text-decoration: none;
  color: inherit;
  border-radius: 14px;
  border: 1px solid transparent;
  background: transparent;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.meme-card--link {
  cursor: pointer;
}

.meme-card--link:hover {
  transform: translateY(-3px);
  border-color: var(--meme-border);
  background: var(--meme-bg-elevated);
  box-shadow: var(--meme-shadow-soft);
}

.meme-card--link:hover .meme-card__title {
  color: var(--meme-primary);
}

.meme-card__cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 14px;
  background: var(--meme-bg-cover);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.meme-card--link:hover .meme-card__cover {
  border-radius: 14px 14px 0 0;
  box-shadow: none;
}

.meme-card__media {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  overflow: hidden;
  z-index: 0;
}

.meme-card__ph {
  width: 100%;
  height: 100%;
}

.meme-card__img,
.meme-card__media :deep(.ui-image),
.meme-card__media :deep(.ui-image img) {
  width: 100%;
  height: 100%;
  display: block;
}

.meme-card__shade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
  height: 46%;
  border-radius: 0 0 inherit inherit;
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.65) 100%);
  pointer-events: none;
}

.meme-card__stats {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  padding: 0 8px 6px;
  pointer-events: none;
  box-sizing: border-box;
}

.meme-card__stats :deep(.meme-card-stats) {
  width: 100%;
}

.meme-card__body {
  padding: 10px 10px 12px;
  min-width: 0;
}

.meme-card__title-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.meme-card__title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--meme-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  transition: color 0.15s ease;
}

.meme-card__meta {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--meme-text-muted);
}

.meme-card__author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  max-width: 100%;
  min-width: 0;
  cursor: pointer;
}

.meme-card__author:hover .meme-card__author-name {
  color: var(--meme-primary);
}

.meme-card__author-avatar {
  flex-shrink: 0;
}

.meme-card__author-name {
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--meme-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}
</style>
