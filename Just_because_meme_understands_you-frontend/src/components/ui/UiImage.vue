<template>
  <div class="ui-image" :style="wrapStyle" @click="$emit('click', $event)">
    <img
      v-if="src"
      :src="src"
      :alt="alt"
      :style="imgStyle"
      loading="lazy"
      @error="onError"
    />
    <div v-else class="ui-image__placeholder">
      <i class="ri-image-line" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiImage',
  props: {
    src: { type: String, default: '' },
    alt: { type: String, default: '' },
    fit: { type: String, default: 'cover' },
    lazy: { type: Boolean, default: false },
  },
  emits: ['click', 'error'],
  computed: {
    wrapStyle() {
      return { overflow: 'hidden' }
    },
    imgStyle() {
      return {
        width: '100%',
        height: '100%',
        objectFit: this.fit,
        display: 'block',
      }
    },
  },
  methods: {
    onError(e) {
      this.$emit('error', e)
    },
  },
}
</script>

<style scoped>
.ui-image {
  position: relative;
  width: 100%;
  height: 100%;
}
.ui-image__placeholder {
  width: 100%;
  height: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--meme-bg-muted);
  color: var(--meme-text-muted);
  font-size: 20px;
}
</style>
