<template>
  <vs-avatar
    class="ui-avatar"
    :class="{ 'has-image': showImage, 'is-circle': circle }"
    :size="size"
    :circle="circle"
    :pointer="pointer"
    :color="showImage ? undefined : color"
  >
    <img v-if="showImage" :src="safeSrc" :alt="alt" @error="onError" />
    <template v-if="!showImage" #text>
      {{ fallbackText }}
    </template>
  </vs-avatar>
</template>

<script>
export default {
  name: 'UiAvatar',
  props: {
    src: { type: String, default: '' },
    size: { type: [Number, String], default: 36 },
    circle: { type: Boolean, default: true },
    pointer: { type: Boolean, default: false },
    color: { type: String, default: 'primary' },
    alt: { type: String, default: '' },
    fallback: { type: String, default: '' },
  },
  data() {
    return { imgError: false }
  },
  computed: {
    safeSrc() {
      const url = this.src != null ? String(this.src).trim() : ''
      return url || ''
    },
    showImage() {
      return !!this.safeSrc && !this.imgError
    },
    fallbackText() {
      const text = this.fallback != null ? String(this.fallback).trim() : ''
      if (text) return text.charAt(0).toUpperCase()
      return 'U'
    },
  },
  watch: {
    src() {
      this.imgError = false
    },
  },
  methods: {
    onError() {
      this.imgError = true
    },
  },
}
</script>

<style scoped>
.ui-avatar {
  display: inline-flex;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  vertical-align: middle;
}

.ui-avatar :deep(.vs-avatar),
.ui-avatar :deep(.vs-avatar__content),
.ui-avatar.has-image :deep(.vs-avatar),
.ui-avatar.has-image :deep(.vs-avatar__content) {
  overflow: hidden;
}

.ui-avatar :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.ui-avatar.has-image :deep(.vs-avatar__content),
.ui-avatar.has-image :deep(.vs-avatar),
.ui-avatar.has-image {
  background: transparent !important;
}

.ui-avatar:not(.has-image) :deep(.vs-avatar),
.ui-avatar:not(.has-image) :deep(.vs-avatar__content) {
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark)) !important;
  color: var(--meme-text-inverse);
  font-weight: 700;
}
</style>
