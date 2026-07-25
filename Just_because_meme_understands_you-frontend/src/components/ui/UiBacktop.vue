<template>
  <button
    v-show="visible"
    type="button"
    class="ui-backtop"
    :style="{ right: right + 'px', bottom: bottom + 'px' }"
    aria-label="回到顶部"
    @click="scrollTop"
  >
    <slot>
      <i class="ri-arrow-up-line" />
    </slot>
  </button>
</template>

<script>
export default {
  name: 'UiBacktop',
  props: {
    visibilityHeight: { type: Number, default: 200 },
    right: { type: Number, default: 40 },
    bottom: { type: Number, default: 40 },
  },
  data() {
    return { visible: false }
  },
  mounted() {
    window.addEventListener('scroll', this.onScroll, { passive: true })
    this.onScroll()
  },
  beforeUnmount() {
    window.removeEventListener('scroll', this.onScroll)
  },
  methods: {
    onScroll() {
      this.visible = window.scrollY >= this.visibilityHeight
    },
    scrollTop() {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
  },
}
</script>

<style scoped>
.ui-backtop {
  position: fixed;
  z-index: 100;
  width: 40px;
  height: 40px;
  border: 1px solid var(--meme-border);
  border-radius: 50%;
  background: var(--meme-bg-elevated);
  color: var(--meme-text);
  box-shadow: var(--meme-shadow-soft);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
</style>
