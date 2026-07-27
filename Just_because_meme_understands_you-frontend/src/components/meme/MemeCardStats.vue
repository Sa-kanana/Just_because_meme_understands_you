<template>
  <div
    class="meme-card-stats"
    :class="[`meme-card-stats--${variant}`, { 'meme-card-stats--spread': spread }]"
    role="group"
    :aria-label="ariaLabel"
  >
    <template v-if="spread">
      <div class="meme-card-stats__group">
        <span
          v-for="item in leftItems"
          :key="item.key"
          class="meme-card-stats__chip"
          :class="item.chipClass"
          :title="item.label"
        >
          <MemeStatIcon :type="item.key" class="meme-card-stats__icon" />
          <span class="meme-card-stats__value">{{ formatCompactNumber(item.value) }}</span>
        </span>
      </div>
      <span
        v-for="item in rightItems"
        :key="item.key"
        class="meme-card-stats__chip meme-card-stats__chip--trail"
        :class="item.chipClass"
        :title="`${item.label} ${formatCompactNumber(item.value)}`"
      >
        <MemeStatIcon :type="item.key" class="meme-card-stats__icon" />
        <span class="meme-card-stats__value">{{ formatCompactNumber(item.value) }}</span>
      </span>
    </template>
    <template v-else>
      <span
        v-for="item in visibleItems"
        :key="item.key"
        class="meme-card-stats__chip"
        :class="item.chipClass"
        :title="item.label"
      >
        <MemeStatIcon :type="item.key" class="meme-card-stats__icon" />
        <span class="meme-card-stats__value">{{ formatCompactNumber(item.value) }}</span>
      </span>
    </template>
  </div>
</template>

<script>
import { formatCompactNumber } from '@/utils/formatNumber'
import MemeStatIcon from '@/components/meme/MemeStatIcon.vue'

function buildItem(key, label, value, chipClass) {
  return { key, label, value, chipClass }
}

export default {
  name: 'MemeCardStats',
  components: {
    MemeStatIcon,
  },
  props: {
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
    variant: {
      type: String,
      default: 'overlay',
      validator: (value) => ['overlay', 'inline'].includes(value),
    },
    spread: {
      type: Boolean,
      default: true,
    },
    showViews: {
      type: Boolean,
      default: true,
    },
    showLikes: {
      type: Boolean,
      default: true,
    },
    showComments: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    ariaLabel() {
      const parts = []
      if (this.showViews) parts.push(`浏览 ${formatCompactNumber(this.pageViews)}`)
      if (this.showComments) parts.push(`评论 ${formatCompactNumber(this.comments)}`)
      if (this.showLikes) parts.push(`点赞 ${formatCompactNumber(this.likes)}`)
      return parts.join('，')
    },
    visibleItems() {
      const items = []
      if (this.showViews) {
        items.push(buildItem('views', '浏览量', this.pageViews, 'is-views'))
      }
      if (this.showComments) {
        items.push(buildItem('comments', '评论', this.comments, 'is-comments'))
      }
      if (this.showLikes) {
        items.push(buildItem('likes', '点赞', this.likes, 'is-likes'))
      }
      return items
    },
    leftItems() {
      return this.visibleItems.filter((item) => item.key !== 'likes')
    },
    rightItems() {
      return this.visibleItems.filter((item) => item.key === 'likes')
    },
  },
  methods: {
    formatCompactNumber,
  },
}
</script>

<style scoped>
.meme-card-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.meme-card-stats--spread {
  width: 100%;
  justify-content: space-between;
  gap: 8px;
}

.meme-card-stats__group {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  flex: 1 1 auto;
}

.meme-card-stats__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  max-width: 100%;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.meme-card-stats__chip--trail {
  flex: 0 0 auto;
  min-width: auto;
  max-width: none;
}

.meme-card-stats__icon {
  flex-shrink: 0;
}

.meme-card-stats__value {
  flex-shrink: 0;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

/* 封面叠层：B 站风格，白字 + 图标，无胶囊底 */
.meme-card-stats--overlay {
  gap: 10px;
}

.meme-card-stats--overlay .meme-card-stats__chip {
  padding: 0;
  font-size: 12px;
  font-weight: 400;
  color: #fff;
  background: transparent;
  border: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.meme-card-stats--overlay .meme-card-stats__icon {
  width: 16px;
  height: 16px;
  color: #fff;
  opacity: 0.95;
}

.meme-card-stats--overlay .meme-card-stats__value {
  font-weight: 400;
}

.meme-card-stats--overlay .meme-card-stats__chip.is-likes {
  color: #fff;
  background: transparent;
  border: none;
}

.meme-card-stats--overlay .meme-card-stats__chip.is-likes .meme-card-stats__icon {
  color: #fff;
}

.meme-card-stats--overlay .meme-card-stats__group {
  gap: 10px;
}

/* 信息区横排 */
.meme-card-stats--inline {
  flex-wrap: nowrap;
  gap: 10px;
}

.meme-card-stats--inline .meme-card-stats__chip {
  padding: 0;
  font-size: 12px;
  color: var(--meme-text-secondary);
  background: transparent;
  border: none;
}

.meme-card-stats--inline .meme-card-stats__icon {
  width: 14px;
  height: 14px;
  color: var(--meme-text-muted);
}

.meme-card-stats--inline .meme-card-stats__chip.is-likes .meme-card-stats__icon {
  color: #f97316;
}

.meme-card-stats--inline .meme-card-stats__value {
  font-weight: 600;
  color: var(--meme-text-secondary);
}
</style>
