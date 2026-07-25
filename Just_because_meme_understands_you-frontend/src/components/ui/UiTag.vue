<template>
  <span class="ui-tag" :class="[`is-${type}`, { 'is-plain': plain }]">
    <slot />
  </span>
</template>

<script>
export default {
  name: 'UiTag',
  props: {
    type: { type: String, default: 'info' },
    plain: { type: Boolean, default: false },
    size: { type: String, default: 'default' },
  },
}
</script>

<style scoped>
.ui-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  border: 1px solid transparent;
}
.ui-tag.is-success {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}
.ui-tag.is-warning {
  background: rgba(245, 158, 11, 0.14);
  color: #d97706;
}
.ui-tag.is-danger {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}
.ui-tag.is-primary {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}
</style>
