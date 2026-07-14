<template>
  <section class="preview-banner" :class="[`preview-banner--${variant}`, { 'is-collapsed': collapsed }]">
    <div class="preview-banner-accent" aria-hidden="true" />

    <div class="preview-banner-top">
      <div class="preview-banner-main">
        <div class="preview-banner-icon-wrap">
          <span class="preview-banner-icon" aria-hidden="true">{{ variantIcon }}</span>
          <span v-if="variant === 'reviewing'" class="preview-banner-pulse" aria-hidden="true" />
        </div>

        <div class="preview-banner-body">
          <div class="preview-banner-head">
            <span class="preview-banner-badge">{{ statusLabel }}</span>
            <h2 class="preview-banner-title">{{ title }}</h2>
          </div>
          <p v-show="!collapsed" class="preview-banner-desc">{{ description }}</p>
        </div>
      </div>

      <div class="preview-banner-top-actions">
        <button
          type="button"
          class="preview-banner-toggle"
          :aria-expanded="!collapsed"
          @click="collapsed = !collapsed"
        >
          {{ collapsed ? '展开说明' : '收起' }}
          <span class="preview-banner-toggle-arrow" :class="{ 'is-up': !collapsed }">⌄</span>
        </button>
      </div>
    </div>

    <div v-show="!collapsed" class="preview-banner-content">
      <div class="preview-banner-grid">
        <div
          v-for="item in checklist"
          :key="item.key"
          class="preview-banner-chip"
          :class="{ 'is-disabled': !item.enabled }"
        >
          <span class="preview-banner-chip-mark">{{ item.enabled ? '✓' : '—' }}</span>
          <span>{{ item.text }}</span>
        </div>
      </div>

      <div class="preview-banner-actions">
        <el-button round @click="$emit('back-published')">回到我的发布</el-button>
        <el-button
          v-if="variant === 'reviewing'"
          type="primary"
          round
          :loading="refreshing"
          @click="$emit('refresh')"
        >
          刷新状态
        </el-button>
      </div>
    </div>
  </section>
</template>

<script>
import { computed, ref } from 'vue'

export default {
  name: 'MemeDetailPreviewBanner',
  props: {
    status: {
      type: Number,
      default: 2,
    },
    statusDesc: {
      type: String,
      default: '',
    },
    refreshing: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['back-published', 'refresh'],
  setup(props) {
    const collapsed = ref(false)

    const variant = computed(() => (Number(props.status) === 3 ? 'offline' : 'reviewing'))

    const statusLabel = computed(() => {
      if (props.statusDesc) return props.statusDesc
      return variant.value === 'offline' ? '已下架' : '审核中'
    })

    const variantIcon = computed(() => (variant.value === 'offline' ? '📦' : '⏳'))

    const title = computed(() => '发布者专属预览')

    const description = computed(() => {
      if (variant.value === 'offline') {
        return '这条梗已退出公域，仅你可见。可在「我的发布」里恢复上架或彻底删除。'
      }
      return '内容正在排队审核，你可以先核对展示效果；通过后才会出现在首页与搜索。'
    })

    const checklist = computed(() => {
      if (variant.value === 'offline') {
        return [
          { key: 'view', enabled: true, text: '可预览完整内容' },
          { key: 'public', enabled: false, text: '公域不可见' },
          { key: 'interact', enabled: false, text: '评论收藏未开放' },
          { key: 'restore', enabled: true, text: '支持恢复或彻底删除' },
        ]
      }
      return [
        { key: 'view', enabled: true, text: '可预览标题 / 封面 / 标签 / 链接' },
        { key: 'public', enabled: false, text: '公域用户暂不可见' },
        { key: 'interact', enabled: false, text: '评论与收藏未开放' },
        { key: 'pass', enabled: true, text: '审核通过后自动上线' },
      ]
    })

    return {
      collapsed,
      variant,
      statusLabel,
      variantIcon,
      title,
      description,
      checklist,
    }
  },
}
</script>

<style scoped>
.preview-banner {
  position: relative;
  overflow: hidden;
  border-radius: var(--meme-radius-lg);
  padding: 16px 18px;
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-gradient-hero);
  box-shadow: var(--meme-shadow-card);
}

.preview-banner--offline {
  border-color: var(--meme-border);
  background: var(--meme-gradient-card);
  box-shadow: var(--meme-shadow-soft);
}

.preview-banner-accent {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, var(--meme-primary), var(--el-color-primary-light-3));
}

.preview-banner--offline .preview-banner-accent {
  background: linear-gradient(180deg, var(--meme-text-secondary), var(--meme-text-muted));
}

.preview-banner-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.preview-banner-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.preview-banner-icon-wrap {
  position: relative;
  flex-shrink: 0;
}

.preview-banner-icon {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  background: var(--meme-surface-ghost);
  border: 1px solid var(--meme-border-accent);
  box-shadow: var(--meme-shadow-soft);
}

.preview-banner--offline .preview-banner-icon {
  border-color: var(--meme-border);
  box-shadow: var(--meme-shadow-soft);
}

.preview-banner-pulse {
  position: absolute;
  inset: -4px;
  border-radius: 18px;
  border: 2px solid var(--meme-border-accent);
  animation: preview-pulse 2.2s ease-out infinite;
}

@keyframes preview-pulse {
  0% {
    transform: scale(0.92);
    opacity: 0.75;
  }
  70% {
    transform: scale(1.08);
    opacity: 0;
  }
  100% {
    transform: scale(1.08);
    opacity: 0;
  }
}

.preview-banner-body {
  flex: 1;
  min-width: 0;
}

.preview-banner-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.preview-banner-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--meme-accent-text);
  background: var(--meme-primary-soft);
}

.preview-banner--offline .preview-banner-badge {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
}

.preview-banner-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--meme-text);
}

.preview-banner-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
}

.preview-banner-top-actions {
  flex-shrink: 0;
}

.preview-banner-toggle {
  border: none;
  background: var(--meme-surface-ghost);
  color: var(--meme-text-secondary);
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 999px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: background 0.2s, color 0.2s;
}

.preview-banner-toggle:hover {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.preview-banner-toggle-arrow {
  display: inline-block;
  transition: transform 0.2s ease;
  font-size: 14px;
  line-height: 1;
}

.preview-banner-toggle-arrow.is-up {
  transform: rotate(180deg);
}

.preview-banner-content {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--meme-border-accent);
}

.preview-banner--offline .preview-banner-content {
  border-top-color: var(--meme-border-strong);
}

.preview-banner.is-collapsed .preview-banner-content {
  display: none;
}

.preview-banner-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.preview-banner-chip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--meme-text-secondary);
  background: var(--meme-surface-ghost);
  border: 1px solid var(--meme-border-accent);
}

.preview-banner-chip.is-disabled {
  color: var(--meme-text-muted);
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
}

.preview-banner-chip-mark {
  flex-shrink: 0;
  width: 14px;
  font-weight: 700;
  color: var(--meme-primary);
}

.preview-banner-chip.is-disabled .preview-banner-chip-mark {
  color: var(--meme-text-muted);
}

.preview-banner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .preview-banner {
    padding: 14px 14px 16px;
  }

  .preview-banner-grid {
    grid-template-columns: 1fr;
  }

  .preview-banner-actions {
    justify-content: stretch;
  }

  .preview-banner-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
