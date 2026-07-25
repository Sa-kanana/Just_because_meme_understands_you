<template>
  <div class="ui-empty">
    <div class="ui-empty__icon" aria-hidden="true">
      <i class="ri-inbox-2-line" />
    </div>
    <p class="ui-empty__desc">{{ description }}</p>
    <div v-if="$slots.default" class="ui-empty__extra">
      <slot />
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiEmpty',
  props: {
    description: { type: String, default: '暂无数据' },
    imageSize: { type: [Number, String], default: 64 },
  },
}
</script>

<style scoped>
.ui-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 12px;
  text-align: center;
  color: var(--meme-text-muted);
}
.ui-empty__icon {
  font-size: 36px;
  margin-bottom: 8px;
  opacity: 0.7;
}
.ui-empty__desc {
  margin: 0;
  font-size: 13px;
}
.ui-empty__extra {
  margin-top: 12px;
}
</style>
