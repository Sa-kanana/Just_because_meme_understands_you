<template>
  <div class="ui-skeleton" :class="{ 'is-animated': animated }">
    <div v-if="!$slots.default" class="ui-skeleton__rows">
      <div v-for="n in rows" :key="n" class="ui-skeleton__line" />
    </div>
    <slot />
  </div>
</template>

<script>
export default {
  name: 'UiSkeleton',
  props: {
    rows: { type: Number, default: 3 },
    animated: { type: Boolean, default: false },
  },
}
</script>

<style scoped>
.ui-skeleton__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ui-skeleton__line {
  height: 12px;
  border-radius: 4px;
  background: var(--meme-bg-muted);
}
.ui-skeleton.is-animated .ui-skeleton__line {
  background: linear-gradient(
    90deg,
    var(--meme-bg-muted) 0%,
    rgba(49, 138, 239, 0.12) 45%,
    var(--meme-bg-muted) 90%
  );
  background-size: 200% 100%;
  animation: ui-skel 1.4s ease-in-out infinite;
}
@keyframes ui-skel {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}
</style>
