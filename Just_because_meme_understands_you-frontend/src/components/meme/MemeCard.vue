<template>
  <component
    :is="rootTag"
    class="meme-card"
    :class="{ 'meme-card--link': isLink }"
    v-bind="rootProps"
    @click="handleClick"
  >
    <div class="meme-card__cover">
      <el-image
        v-if="image"
        :src="image"
        :alt="displayName"
        fit="cover"
        class="meme-card__img"
        :lazy="lazy"
      >
        <template #error>
          <div class="meme-card__placeholder">
            <MemeStatIcon type="views" class="meme-card__placeholder-icon" />
            <span>封面加载失败</span>
          </div>
        </template>
      </el-image>
      <div v-else class="meme-card__placeholder">
        <MemeStatIcon type="views" class="meme-card__placeholder-icon" />
        <span>暂无封面</span>
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
          <el-avatar :size="20" :src="authorAvatar" class="meme-card__author-avatar">
            {{ authorFallback }}
          </el-avatar>
          <span class="meme-card__author-name">{{ authorNickname }}</span>
        </div>
        <p v-else-if="footerText" class="meme-card__meta">{{ footerText }}</p>
      </slot>
    </div>
  </component>
</template>

<script>
import MemeCardStats from '@/components/meme/MemeCardStats.vue'
import MemeStatIcon from '@/components/meme/MemeStatIcon.vue'
import { formatDateShort } from '@/utils/formatDate'
import { buildUserProfileLocation } from '@/utils/pageBreadcrumb'

export default {
  name: 'MemeCard',
  components: {
    MemeCardStats,
    MemeStatIcon,
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
  border-radius: 6px;
  transition: transform 0.15s ease;
}

.meme-card--link {
  cursor: pointer;
}

.meme-card--link:hover {
  transform: translateY(-1px);
}

.meme-card--link:hover .meme-card__title {
  color: var(--meme-primary);
}

.meme-card__cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 6px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 22%, var(--meme-primary-soft), transparent 42%),
    var(--meme-bg-cover);
}

.meme-card__img,
.meme-card__img :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
  display: block;
}

.meme-card__img {
  height: 100%;
}

.meme-card__placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--meme-text-muted);
  font-size: 12px;
}

.meme-card__placeholder-icon {
  width: 28px;
  height: 28px;
  color: var(--meme-border-strong);
  opacity: 0.7;
}

.meme-card__shade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 46%;
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
}

.meme-card__stats :deep(.meme-card-stats) {
  width: 100%;
}

.meme-card__body {
  padding: 8px 2px 0;
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
  font-weight: 500;
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
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 10px;
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
