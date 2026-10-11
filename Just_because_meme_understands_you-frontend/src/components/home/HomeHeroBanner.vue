<template>
  <section class="home-hero" aria-label="首页推荐">
    <div class="home-hero__shell">
      <div class="home-hero__copy">
        <svg class="home-hero__decoration-dots" viewBox="0 0 88 64" aria-hidden="true">
          <g fill="currentColor">
            <circle cx="6" cy="6" r="3" /><circle cx="28" cy="6" r="3" /><circle cx="50" cy="6" r="3" /><circle cx="72" cy="6" r="3" />
            <circle cx="6" cy="28" r="3" /><circle cx="28" cy="28" r="3" /><circle cx="50" cy="28" r="3" /><circle cx="72" cy="28" r="3" />
            <circle cx="6" cy="50" r="3" /><circle cx="28" cy="50" r="3" /><circle cx="50" cy="50" r="3" /><circle cx="72" cy="50" r="3" />
          </g>
        </svg>
        <h1 class="home-hero__title">
          只因<span class="home-hero__title-accent">“梗”<svg class="home-hero__title-underline" viewBox="0 0 96 18" aria-hidden="true"><path d="M2 14C23 13 33 4 52 4c11 0 8 9 17 9 8 0 15-6 25-8" /></svg></span>懂你
        </h1>
        <p class="home-hero__tagline">万物皆可梗，只有我懂你。</p>
        <p class="home-hero__lead">
          欢迎来到你的互联网冲浪避风港。实时热门梗图与为你定制的幽默内容，发现今日快乐，探寻梗文化源头。
        </p>
        <div class="home-hero__actions">
          <vs-button color="primary" class="home-hero__cta home-hero__cta--primary" @click="$emit('publish')">
            <i class="ri-add-line" aria-hidden="true" />
            发布梗图
          </vs-button>
          <vs-button type="border" color="primary" class="home-hero__cta home-hero__cta--ghost" @click="$emit('explore')">
            <i class="ri-compass-3-line" aria-hidden="true" />
            探索热梗
          </vs-button>
        </div>
        <svg class="home-hero__star" viewBox="0 0 32 32" aria-hidden="true">
          <path d="M16 0c2.4 8.3 4.7 10.6 16 16-11.3 5.4-13.6 7.7-16 16C13.6 23.7 11.3 21.4 0 16 11.3 10.6 13.6 8.3 16 0Z" />
        </svg>
        <svg class="home-hero__bottom-art" viewBox="0 0 420 88" aria-hidden="true">
          <text x="4" y="65">Meme</text>
          <path d="M10 78C78 51 108 31 154 33c28 1 27 18 3 32-23 13-9 18 26 3 56-24 120-48 215-56" />
        </svg>
      </div>

      <div class="home-hero__media" @mouseenter="pauseRotate" @mouseleave="resumeRotate">
        <div v-if="loading" class="home-hero__state">
          <span class="home-hero__spinner" aria-hidden="true"></span>
          <span>推荐加载中...</span>
        </div>
        <div v-else-if="error" class="home-hero__state home-hero__state--error" role="status">
          <div class="home-hero__offline-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M2.5 8.8a15 15 0 0 1 15.9-3.4M5.8 12a9.8 9.8 0 0 1 7.4-2.1M9.2 15.3a4.7 4.7 0 0 1 2.4-.6M3 3l18 18" />
            </svg>
          </div>
          <div class="home-hero__offline-copy">
            <span class="home-hero__offline-label">首页推荐</span>
            <strong>热门推荐暂时离线</strong>
            <span class="home-hero__offline-hint">启动后端服务后，可以重新加载推荐内容。</span>
            <vs-button
              type="border"
              color="primary"
              class="home-hero__offline-retry"
              :disabled="loading"
              @click="loadSlides"
            >
              重试推荐
            </vs-button>
          </div>
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
              <span class="home-hero__backdrop" aria-hidden="true">
                <ui-image :src="item.img_url" alt="" fit="cover" />
              </span>
              <ui-image
                v-if="!slideErrors[index]"
                :src="item.img_url"
                :alt="item.title || '推荐图'"
                fit="contain"
                class="home-hero__image"
                @error="onSlideError(index)"
              />
              <span v-else class="home-hero__image-fallback">{{ item.title || '图片加载失败' }}</span>
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
      slideErrors: {},
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
        this.slideErrors = {}
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
    onSlideError(index) {
      this.slideErrors = { ...this.slideErrors, [index]: true }
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
  width: 100%;
  margin: 0 auto 14px;
  overflow: hidden;
}

.home-hero__shell {
  display: grid;
  grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
  gap: 0;
  height: clamp(300px, 23vw, 390px);
  min-height: 0;
  border-radius: 24px;
  border: 1px solid color-mix(in srgb, var(--meme-border) 82%, var(--meme-primary-soft));
  background: linear-gradient(
    135deg,
    var(--meme-bg-card) 0%,
    color-mix(in srgb, var(--meme-bg-card) 94%, var(--meme-primary-soft)) 100%
  );
  box-shadow: 0 14px 34px rgba(27, 69, 125, 0.075);
  overflow: hidden;
}

.home-hero__copy {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 26px clamp(28px, 3.8vw, 54px);
  background: var(--meme-gradient-hero);
  overflow: hidden;
}

.home-hero__copy::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 10% 92%, transparent 0 48px, var(--meme-border-accent) 49px 50px, transparent 51px);
  opacity: 0.48;
  pointer-events: none;
}

.home-hero__decoration-dots {
  position: absolute;
  z-index: 1;
  top: 22px;
  right: 26px;
  width: 72px;
  height: 54px;
  color: var(--meme-border-accent);
  pointer-events: none;
}

.home-hero__copy::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 6% 92%, color-mix(in srgb, var(--meme-primary-soft) 86%, transparent), transparent 48%),
    radial-gradient(ellipse at 88% 12%, color-mix(in srgb, var(--meme-primary-soft) 72%, transparent), transparent 36%),
    linear-gradient(125deg, transparent 12%, color-mix(in srgb, var(--meme-bg-card) 36%, transparent) 62%, transparent 100%);
  pointer-events: none;
}

.home-hero__title {
  position: relative;
  z-index: 1;
  margin: 0 0 10px;
  font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', 'PingFang SC', 'Noto Sans CJK SC', sans-serif;
  font-size: clamp(40px, 4vw, 64px);
  font-weight: 900;
  line-height: 1.06;
  letter-spacing: -0.045em;
  color: var(--meme-text);
}

.home-hero__title-accent {
  position: relative;
  display: inline-block;
  margin: 0 0.08em;
  font-weight: 900;
  background: linear-gradient(120deg, var(--meme-primary-dark) 0%, var(--meme-primary) 58%, #35b7f2 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.home-hero__title-underline {
  position: absolute;
  left: 37%;
  bottom: -0.02em;
  width: 68%;
  height: 0.25em;
  overflow: visible;
  fill: none;
  stroke: #8c69ed;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  pointer-events: none;
}

.home-hero__star {
  position: absolute;
  z-index: 1;
  top: 45%;
  right: 15%;
  width: 25px;
  height: 25px;
  color: var(--meme-primary);
  fill: currentColor;
  filter: drop-shadow(0 3px 5px var(--meme-focus-ring));
  pointer-events: none;
}

.home-hero__bottom-art {
  position: absolute;
  z-index: 0;
  left: 20px;
  bottom: -1px;
  width: min(420px, 88%);
  height: 76px;
  overflow: visible;
  color: #74a8ff;
  opacity: 0.72;
  pointer-events: none;
}

.home-hero__bottom-art text {
  fill: currentColor;
  font-family: 'Segoe Script', 'Bradley Hand', 'Comic Sans MS', cursive;
  font-size: 50px;
  font-style: italic;
  font-weight: 500;
}

.home-hero__bottom-art path {
  fill: none;
  stroke: currentColor;
  stroke-width: 2.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.home-hero__tagline {
  position: relative;
  z-index: 1;
  margin: 0 0 18px;
  font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', 'PingFang SC', 'Noto Sans CJK SC', sans-serif;
  font-size: clamp(17px, 1.45vw, 21px);
  font-weight: 500;
  line-height: 1.5;
  color: var(--meme-text-secondary);
}

.home-hero__lead {
  display: none;
}

.home-hero__actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.home-hero__cta.vs-button {
  min-width: 142px;
  height: 42px !important;
  margin: 0;
  padding: 0 18px !important;
  font-size: clamp(16px, 1.15vw, 19px) !important;
  font-weight: 700 !important;
  border-radius: 12px !important;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease, border-color 0.18s ease !important;
}

.home-hero__cta :deep(.vs-button__content) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.home-hero__cta i {
  font-size: 17px;
  line-height: 1;
}

.home-hero__cta--primary.vs-button {
  color: #fff !important;
  background: linear-gradient(130deg, var(--meme-primary) 0%, var(--meme-primary-dark) 100%) !important;
  box-shadow: 0 9px 20px var(--meme-focus-ring) !important;
}

.home-hero__cta--ghost.vs-button {
  border: 1.5px solid var(--meme-primary) !important;
  color: var(--meme-primary) !important;
  background: var(--meme-bg-card) !important;
  box-shadow: 0 3px 9px rgba(27, 69, 125, 0.035) !important;
}

.home-hero__cta--primary.vs-button:hover:not(:disabled),
.home-hero__cta--ghost.vs-button:hover:not(:disabled) {
  transform: translateY(-2px);
}

.home-hero__cta--primary.vs-button:hover:not(:disabled) {
  box-shadow: 0 13px 26px var(--meme-focus-ring) !important;
}

.home-hero__cta--ghost.vs-button:hover:not(:disabled) {
  background: var(--meme-primary-soft) !important;
  box-shadow: 0 8px 18px rgba(27, 69, 125, 0.09) !important;
}

.home-hero__cta--primary.vs-button:active:not(:disabled),
.home-hero__cta--ghost.vs-button:active:not(:disabled) {
  transform: translateY(-0.5px);
}

.home-hero__media {
  position: relative;
  height: 100%;
  aspect-ratio: auto;
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
  position: relative;
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

.home-hero__backdrop {
  position: absolute;
  inset: -12px;
  display: block;
  overflow: hidden;
  filter: blur(14px);
  opacity: 0.5;
  transform: scale(1.04);
  pointer-events: none;
}

.home-hero__backdrop :deep(.ui-image),
.home-hero__backdrop :deep(.ui-image img) {
  display: block;
  width: 100%;
  height: 100%;
}

.home-hero__backdrop :deep(.ui-image img) {
  object-fit: cover;
}

.home-hero__slide-btn.is-clickable {
  cursor: pointer;
}

.home-hero__image,
.home-hero__slide-btn :deep(.ui-image) {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  display: block;
}

.home-hero__slide-btn :deep(.ui-image img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.home-hero__caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 48px 20px 18px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
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
  color: #fff;
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
  background: #fff;
}

.home-hero__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 100%;
  color: var(--meme-text-muted);
  font-size: 14px;
  background: var(--meme-bg-cover);
}

.home-hero__state--error {
  gap: clamp(16px, 3vw, 28px);
  padding: 24px;
  background:
    radial-gradient(circle at 86% 16%, var(--meme-primary-soft), transparent 34%),
    linear-gradient(135deg, var(--meme-bg-muted), var(--meme-bg-card));
}

.home-hero__offline-mark {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  width: 78px;
  height: 78px;
  place-items: center;
  border: 1px solid var(--meme-border-accent);
  border-radius: 25px;
  background: linear-gradient(145deg, var(--meme-bg-card), var(--meme-primary-soft));
  color: var(--meme-primary);
  box-shadow: var(--meme-shadow-soft);
}

.home-hero__offline-mark::before,
.home-hero__offline-mark::after {
  position: absolute;
  border: 1px solid var(--meme-border-accent);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.home-hero__offline-mark::before {
  inset: -9px;
  opacity: 0.55;
}

.home-hero__offline-mark::after {
  top: 3px;
  right: 2px;
  width: 7px;
  height: 7px;
  border: 0;
  background: var(--meme-warning);
  box-shadow: 0 0 0 4px var(--meme-warning-soft);
}

.home-hero__offline-mark svg {
  width: 31px;
  height: 31px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.home-hero__offline-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  max-width: 340px;
}

.home-hero__offline-label {
  color: var(--meme-primary);
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.home-hero__offline-copy strong {
  color: var(--meme-text);
  font-size: 18px;
  font-weight: 750;
  line-height: 1.35;
}

.home-hero__offline-hint {
  color: var(--meme-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.home-hero__offline-retry.vs-button {
  min-height: 34px;
  margin-top: 3px;
  padding: 0 13px !important;
  border-radius: 10px !important;
  font-size: 12px !important;
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
  .home-hero {
    width: 100%;
  }

  .home-hero__shell {
    grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
    height: clamp(280px, 34vw, 340px);
    min-height: 0;
    max-height: none;
  }

  .home-hero__copy {
    padding: 22px clamp(16px, 2.2vw, 26px);
  }

  .home-hero__title {
    font-size: clamp(30px, 4.7vw, 46px);
  }

  .home-hero__tagline {
    font-size: 16px;
    margin-bottom: 12px;
  }

  .home-hero__media {
    height: 100%;
    aspect-ratio: auto;
  }

  .home-hero__actions {
    gap: 6px;
  }

  .home-hero__cta.vs-button {
    flex: 1 1 auto;
    min-width: 0;
    padding: 0 8px !important;
    font-size: clamp(13px, 1.5vw, 16px) !important;
  }
}

@media (max-width: 640px) {
  .home-hero__shell {
    grid-template-columns: 1fr;
    height: auto;
  }

  .home-hero__media {
    height: auto;
    aspect-ratio: 2.2 / 1;
  }

  .home-hero__title-underline {
    bottom: -0.04em;
    stroke-width: 2.4;
  }

  .home-hero__star {
    top: auto;
    right: 9%;
    bottom: 18px;
    width: 19px;
    height: 19px;
  }

  .home-hero__bottom-art {
    display: none;
  }
}

@media (max-width: 768px) {
  .home-hero__copy {
    padding: 22px 24px;
  }

  .home-hero__title {
    font-size: clamp(28px, 7.4vw, 36px);
  }

  .home-hero__actions {
    gap: 8px;
  }

  .home-hero__cta.vs-button {
    flex: 1;
    min-width: 0;
  }

  .home-hero__offline-mark {
    width: 62px;
    height: 62px;
    border-radius: 20px;
  }

  .home-hero__offline-mark svg {
    width: 25px;
    height: 25px;
  }

  .home-hero__nav {
    opacity: 1;
    width: 28px;
    height: 28px;
    font-size: 18px;
  }

  .home-hero__state--error {
    gap: 10px;
    padding: 18px 12px;
  }

  .home-hero__offline-mark {
    width: 48px;
    height: 48px;
    border-radius: 16px;
  }

  .home-hero__offline-mark svg {
    width: 21px;
    height: 21px;
  }

  .home-hero__offline-copy {
    max-width: 230px;
    gap: 5px;
  }

  .home-hero__offline-copy strong {
    font-size: 14px;
  }

  .home-hero__offline-hint {
    font-size: 11px;
  }
}
</style>
