<template>
  <div
    class="meme-cover-ph"
    :class="{ 'is-abstract': abstract }"
    :style="toneStyle"
    aria-hidden="true"
  >
    <span class="meme-cover-ph__glow" />
    <span class="meme-cover-ph__ring" />
    <template v-if="!abstract">
      <span class="meme-cover-ph__mark">{{ mark }}</span>
      <span v-if="caption" class="meme-cover-ph__caption">{{ caption }}</span>
    </template>
    <i v-else class="ri-image-2-line meme-cover-ph__icon" />
  </div>
</template>

<script>
const TONES = [
  { from: '#3b82f6', to: '#0ea5e9' },
  { from: '#0d9488', to: '#14b8a6' },
  { from: '#2563eb', to: '#38bdf8' },
  { from: '#059669', to: '#34d399' },
  { from: '#0284c7', to: '#67e8f9' },
  { from: '#1d4ed8', to: '#22d3ee' },
]

function hashTone(seed) {
  const text = seed != null ? String(seed) : ''
  let h = 0
  for (let i = 0; i < text.length; i += 1) {
    h = (h * 31 + text.charCodeAt(i)) >>> 0
  }
  return TONES[h % TONES.length]
}

export default {
  name: 'MemeCoverPlaceholder',
  props: {
    name: { type: String, default: '' },
    seed: { type: [String, Number], default: '' },
    showCaption: { type: Boolean, default: false },
    /** 抽象图案占位，避免与下方标题重复字 */
    abstract: { type: Boolean, default: false },
  },
  computed: {
    mark() {
      const text = this.name != null ? String(this.name).trim() : ''
      if (!text) return '梗'
      return text.slice(0, text.length >= 2 && /[\u4e00-\u9fff]/.test(text) ? 2 : 1)
    },
    caption() {
      if (!this.showCaption) return ''
      const text = this.name != null ? String(this.name).trim() : ''
      return text || '未命名梗图'
    },
    toneStyle() {
      const tone = hashTone(this.seed || this.name)
      return {
        '--ph-from': tone.from,
        '--ph-to': tone.to,
      }
    },
  },
}
</script>

<style scoped>
.meme-cover-ph {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 20%, rgba(255, 255, 255, 0.28), transparent 42%),
    linear-gradient(145deg, var(--ph-from), var(--ph-to));
  color: #fff;
}

.meme-cover-ph__glow {
  position: absolute;
  right: -18%;
  bottom: -28%;
  width: 70%;
  height: 70%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  filter: blur(2px);
}

.meme-cover-ph__ring {
  position: absolute;
  left: 12%;
  top: 18%;
  width: 42%;
  height: 42%;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.22);
  opacity: 0.7;
}

.meme-cover-ph__mark {
  position: relative;
  z-index: 1;
  font-size: clamp(28px, 8vw, 42px);
  font-weight: 800;
  letter-spacing: 0.04em;
  line-height: 1;
  text-shadow: 0 8px 24px rgba(15, 23, 42, 0.22);
}

.meme-cover-ph__caption {
  position: relative;
  z-index: 1;
  max-width: 80%;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  opacity: 0.92;
  text-align: center;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meme-cover-ph__icon {
  position: relative;
  z-index: 1;
  font-size: 28px;
  opacity: 0.88;
  filter: drop-shadow(0 6px 14px rgba(15, 23, 42, 0.2));
}

.meme-cover-ph.is-abstract .meme-cover-ph__ring {
  opacity: 1;
}
</style>
