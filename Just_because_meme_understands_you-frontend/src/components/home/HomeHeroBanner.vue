<template>
  <section class="home-hero" aria-label="首页推荐">
    <div class="home-hero__shell">
      <div class="home-hero__copy">
        <span class="home-hero__eyebrow">互联网梗文化社区</span>
        <h1 class="home-hero__title">
          只因<span class="home-hero__title-accent">“梗”</span>懂你
        </h1>
        <p class="home-hero__tagline">万物皆可梗，只有我懂你。</p>
        <p class="home-hero__lead">
          欢迎来到你的互联网冲浪避风港。实时热门梗图与为你定制的幽默内容，发现今日快乐，探寻梗文化源头。
        </p>
        <div class="home-hero__actions">
          <el-button type="primary" size="large" class="home-hero__btn-primary" @click="$emit('explore')">
            探索热梗
          </el-button>
          <el-button size="large" class="home-hero__btn-secondary" @click="$emit('publish')">
            发布梗图
          </el-button>
        </div>
      </div>

      <div class="home-hero__media" @mouseenter="pauseRotate" @mouseleave="resumeRotate">
        <div v-if="loading" class="home-hero__state">
          <span class="home-hero__spinner" aria-hidden="true"></span>
          <span>推荐加载中...</span>
        </div>
        <div v-else-if="error" class="home-hero__state home-hero__state--error">
          <span>{{ error }}</span>
        </div>
        <div v-else-if="!slides.length" class="home-hero__state">
          <span>暂无推荐内容</span>
        </div>
        <template v-else>
          <div
            v-for="(item, index) in slides"
            :key="slideKey(item, index)"
            class="home-hero__slide"
            :class="{ 'is-active': index === activeIndex }"
          >
            <button
              type="button"
              class="home-hero__slide-btn"
              :class="{ 'is-clickable': isClickable(item) }"
              :disabled="!isClickable(item)"
              @click="handleSlideClick(item)"
            >
              <el-image
                :src="item.img_url"
                :alt="item.title || '推荐图'"
                fit="cover"
                class="home-hero__image"
              >
                <template #error>
                  <span class="home-hero__image-fallback">{{ item.title || '图片加载失败' }}</span>
                </template>
              </el-image>
              <span v-if="item.title" class="home-hero__caption">{{ item.title }}</span>
            </button>
          </div>

          <div v-if="slides.length > 1" class="home-hero__controls">
            <button
              type="button"
              class="home-hero__nav home-hero__nav--prev"
              aria-label="上一张"
              @click="goPrev"
            >
              ‹
            </button>
            <button
              type="button"
              class="home-hero__nav home-hero__nav--next"
              aria-label="下一张"
              @click="goNext"
            >
              ›
            </button>
            <div class="home-hero__dots" role="tablist" aria-label="推荐图切换">
              <button
                v-for="(item, index) in slides"
                :key="`dot-${slideKey(item, index)}`"
                type="button"
                class="home-hero__dot"
                :class="{ 'is-active': index === activeIndex }"
                :aria-label="`第 ${index + 1} 张`"
                :aria-selected="index === activeIndex"
                @click="goTo(index)"
              />
            </div>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script>
import { getHomeImages } from '@/api/homeImage'
import { sanitizeExternalUrl } from '@/utils/safeUrl'
import { buildMemeDetailLocation } from '@/utils/pageBreadcrumb'

/** 跳转类型：0-无跳转，1-内部梗ID，2-外部链接 */
const TARGET_TYPE = { NONE: 0, INTERNAL: 1, EXTERNAL: 2 }
const ROTATE_MS = 6000

export default {
  name: 'HomeHeroBanner',
  emits: ['explore', 'publish'],
  data() {
    return {
      slides: [],
      loading: false,
      error: '',
      activeIndex: 0,
      rotateTimer: null,
      rotatePaused: false,
    }
  },
  mounted() {
    this.loadSlides()
  },
  beforeUnmount() {
    this.clearRotate()
  },
  methods: {
    async loadSlides() {
      this.loading = true
      this.error = ''
      try {
        this.slides = await getHomeImages()
        this.activeIndex = 0
        this.setupRotate()
      } catch (e) {
        this.error = e.message || '推荐内容加载失败'
        this.slides = []
      } finally {
        this.loading = false
      }
    },
    slideKey(item, index) {
      return item.id != null ? item.id : `img-${index}-${item.img_url || ''}`
    },
    isClickable(item) {
      const type = Number(item.target_type)
      if (type === TARGET_TYPE.NONE) return false
      return !!String(item.target_value || '').trim()
    },
    handleSlideClick(item) {
      if (!this.isClickable(item)) return
      const type = Number(item.target_type)
      const value = String(item.target_value || '').trim()
      if (type === TARGET_TYPE.INTERNAL && value) {
        this.$router.push(buildMemeDetailLocation(value, { from: 'banner' }))
        return
      }
      if (type === TARGET_TYPE.EXTERNAL && value) {
        const safeUrl = sanitizeExternalUrl(value)
        if (safeUrl) window.open(safeUrl, '_blank', 'noopener,noreferrer')
      }
    },
    goTo(index) {
      if (!this.slides.length) return
      this.activeIndex = ((index % this.slides.length) + this.slides.length) % this.slides.length
      this.setupRotate()
    },
    goPrev() {
      this.goTo(this.activeIndex - 1)
    },
    goNext() {
      this.goTo(this.activeIndex + 1)
    },
    setupRotate() {
      this.clearRotate()
      if (this.slides.length <= 1 || this.rotatePaused) return
      this.rotateTimer = window.setInterval(() => {
        this.activeIndex = (this.activeIndex + 1) % this.slides.length
      }, ROTATE_MS)
    },
    clearRotate() {
      if (this.rotateTimer != null) {
        window.clearInterval(this.rotateTimer)
        this.rotateTimer = null
      }
    },
    pauseRotate() {
      this.rotatePaused = true
      this.clearRotate()
    },
    resumeRotate() {
      this.rotatePaused = false
      this.setupRotate()
    },
  },
}
</script>

<style scoped>
.home-hero {
  margin-bottom: 24px;
  overflow: hidden;
}

.home-hero__shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0;
  min-height: 360px;
  border-radius: 20px;
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
  overflow: hidden;
}

.home-hero__copy {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 36px 40px;
  background: var(--meme-gradient-hero);
  overflow: hidden;
}

.home-hero__copy::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--meme-hero-fade);
  pointer-events: none;
}

.home-hero__eyebrow {
  position: relative;
  z-index: 1;
  display: inline-flex;
  width: fit-content;
  margin-bottom: 16px;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--meme-accent-text);
  background: var(--meme-primary-soft);
  border: 1px solid var(--meme-border-accent);
}

.home-hero__title {
  position: relative;
  z-index: 1;
  margin: 0 0 14px;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.01em;
  color: var(--meme-text);
}

.home-hero__title-accent {
  background: linear-gradient(120deg, var(--meme-primary-dark) 0%, var(--meme-primary) 45%, #52c7b8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.home-hero__tagline {
  position: relative;
  z-index: 1;
  margin: 0 0 14px;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--meme-primary);
}

.home-hero__lead {
  position: relative;
  z-index: 1;
  margin: 0 0 24px;
  max-width: 28em;
  font-size: 15px;
  line-height: 1.75;
  color: var(--meme-text-secondary);
}

.home-hero__actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.home-hero__btn-primary {
  min-width: 120px;
  border-radius: 12px !important;
  font-weight: 600;
}

.home-hero__btn-secondary {
  min-width: 120px;
  border-radius: 12px !important;
  font-weight: 600;
  color: var(--meme-text);
  border-color: var(--meme-border-strong);
  background: var(--meme-surface-ghost);
}

.home-hero__media {
  position: relative;
  min-height: 360px;
  background: var(--meme-bg-cover);
  overflow: hidden;
}

.home-hero__slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.65s ease;
  pointer-events: none;
}

.home-hero__slide.is-active {
  opacity: 1;
  pointer-events: auto;
  z-index: 1;
}

.home-hero__slide-btn {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  margin: 0;
  border: none;
  background: transparent;
  cursor: default;
  text-align: left;
}

.home-hero__slide-btn.is-clickable {
  cursor: pointer;
}

.home-hero__image,
.home-hero__slide-btn :deep(.el-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.home-hero__slide-btn :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home-hero__caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 48px 20px 18px;
  font-size: 15px;
  font-weight: 600;
  color: var(--meme-text-inverse);
  background: linear-gradient(to top, rgba(15, 23, 42, 0.72) 0%, transparent 100%);
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}

.home-hero__image-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--meme-text-muted);
  font-size: 14px;
  background: var(--meme-bg-cover);
}

.home-hero__controls {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}

.home-hero__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 50%;
  background: var(--meme-overlay);
  color: var(--meme-text-inverse);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, background 0.2s ease;
  pointer-events: auto;
}

.home-hero__media:hover .home-hero__nav {
  opacity: 1;
}

.home-hero__nav:hover {
  background: rgba(15, 23, 42, 0.62);
}

.home-hero__nav--prev {
  left: 14px;
}

.home-hero__nav--next {
  right: 14px;
}

.home-hero__dots {
  position: absolute;
  right: 16px;
  bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  pointer-events: auto;
}

.home-hero__dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: width 0.2s ease, background 0.2s ease;
}

.home-hero__dot.is-active {
  width: 22px;
  background: var(--meme-text-inverse);
}

.home-hero__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 100%;
  min-height: 360px;
  color: var(--meme-text-muted);
  font-size: 14px;
  background: var(--meme-bg-cover);
}

.home-hero__state--error {
  color: var(--meme-danger);
}

.home-hero__spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--meme-border-strong);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: home-hero-spin 0.8s linear infinite;
}

@keyframes home-hero-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 992px) {
  .home-hero__shell {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .home-hero__copy {
    padding: 28px 24px 26px;
  }

  .home-hero__copy::after {
    display: none;
  }

  .home-hero__title {
    font-size: 32px;
  }

  .home-hero__tagline {
    font-size: 18px;
  }

  .home-hero__media,
  .home-hero__state {
    min-height: 260px;
  }
}

@media (max-width: 768px) {
  .home-hero__copy {
    padding: 22px 18px 20px;
  }

  .home-hero__title {
    font-size: 28px;
  }

  .home-hero__lead {
    margin-bottom: 18px;
    font-size: 14px;
  }

  .home-hero__actions {
    gap: 10px;
  }

  .home-hero__btn-primary,
  .home-hero__btn-secondary {
    flex: 1;
    min-width: 0;
  }

  .home-hero__media,
  .home-hero__state {
    min-height: 220px;
  }

  .home-hero__nav {
    opacity: 1;
    width: 32px;
    height: 32px;
    font-size: 20px;
  }
}
</style>
