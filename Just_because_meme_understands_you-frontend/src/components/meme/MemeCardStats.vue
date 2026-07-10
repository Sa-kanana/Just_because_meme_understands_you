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
        class="meme-card-stats__chip"
        :class="item.chipClass"
        :title="item.label"
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
      default: false,
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
}

.meme-card-stats__group {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
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

.meme-card-stats__icon {
  flex-shrink: 0;
}

.meme-card-stats__value {
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

/* 封面叠层：毛玻璃胶囊 */
.meme-card-stats--overlay {
  gap: 5px;
}

.meme-card-stats--overlay .meme-card-stats__chip {
  padding: 4px 9px 4px 7px;
  font-size: 12px;
  color: #f8fafc;
  background: rgba(15, 23, 42, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.meme-card-stats--overlay .meme-card-stats__icon {
  width: 14px;
  height: 14px;
  color: #e2e8f0;
}

.meme-card-stats--overlay .meme-card-stats__chip.is-likes {
  color: #fff7ed;
  background: rgba(234, 88, 12, 0.38);
  border-color: rgba(255, 237, 213, 0.22);
}

.meme-card-stats--overlay .meme-card-stats__chip.is-likes .meme-card-stats__icon {
  color: #fdba74;
}

/* 信息区横排 */
.meme-card-stats--inline {
  flex-wrap: nowrap;
  gap: 10px;
}

.meme-card-stats--inline .meme-card-stats__chip {
  padding: 0;
  font-size: 12px;
  color: #64748b;
  background: transparent;
  border: none;
}

.meme-card-stats--inline .meme-card-stats__icon {
  width: 14px;
  height: 14px;
  color: #94a3b8;
}

.meme-card-stats--inline .meme-card-stats__chip.is-likes .meme-card-stats__icon {
  color: #f97316;
}

.meme-card-stats--inline .meme-card-stats__value {
  font-weight: 600;
  color: #475569;
}
</style>
