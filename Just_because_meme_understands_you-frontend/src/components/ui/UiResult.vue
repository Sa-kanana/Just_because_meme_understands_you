<template>
  <div class="ui-result">
    <div class="ui-result__icon" :class="`is-${icon}`">
      <i :class="iconClass" />
    </div>
    <h3 v-if="title" class="ui-result__title">{{ title }}</h3>
    <p v-if="subTitle" class="ui-result__sub">{{ subTitle }}</p>
    <div v-if="$slots.extra" class="ui-result__extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script>
const ICONS = {
  success: 'ri-checkbox-circle-line',
  warning: 'ri-error-warning-line',
  error: 'ri-close-circle-line',
  info: 'ri-information-line',
}

export default {
  name: 'UiResult',
  props: {
    icon: { type: String, default: 'info' },
    title: { type: String, default: '' },
    subTitle: { type: String, default: '' },
  },
  computed: {
    iconClass() {
      return ICONS[this.icon] || ICONS.info
    },
  },
}
</script>

<style scoped>
.ui-result {
  text-align: center;
  padding: 32px 16px;
}
.ui-result__icon {
  font-size: 48px;
  margin-bottom: 12px;
  color: var(--meme-primary);
}
.ui-result__icon.is-success {
  color: #16a34a;
}
.ui-result__icon.is-warning {
  color: #d97706;
}
.ui-result__icon.is-error {
  color: #dc2626;
}
.ui-result__title {
  margin: 0 0 8px;
  font-size: 18px;
}
.ui-result__sub {
  margin: 0;
  color: var(--meme-text-secondary);
  font-size: 14px;
}
.ui-result__extra {
  margin-top: 16px;
}
</style>
