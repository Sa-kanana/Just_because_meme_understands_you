<template>
  <div class="ui-progress">
    <div class="ui-progress__bar" :style="{ width: percent + '%' }" />
  </div>
</template>

<script>
export default {
  name: 'UiProgress',
  props: {
    percentage: { type: Number, default: 0 },
    strokeWidth: { type: Number, default: 6 },
  },
  computed: {
    percent() {
      return Math.max(0, Math.min(100, Number(this.percentage) || 0))
    },
  },
}
</script>

<style scoped>
.ui-progress {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: var(--meme-bg-muted);
  overflow: hidden;
}
.ui-progress__bar {
  height: 100%;
  background: var(--meme-primary);
  transition: width 0.2s ease;
}
</style>
