<template>
  <span class="ui-badge" :class="{ 'is-dot': isDot }">
    <slot />
    <sup v-if="show" class="ui-badge__content">{{ display }}</sup>
  </span>
</template>

<script>
export default {
  name: 'UiBadge',
  props: {
    value: { type: [String, Number], default: '' },
    max: { type: Number, default: 99 },
    isDot: { type: Boolean, default: false },
    hidden: { type: Boolean, default: false },
  },
  computed: {
    show() {
      if (this.hidden) return false
      if (this.isDot) return true
      return this.value !== '' && this.value != null && Number(this.value) !== 0
    },
    display() {
      if (this.isDot) return ''
      const n = Number(this.value)
      if (!Number.isNaN(n) && n > this.max) return `${this.max}+`
      return this.value
    },
  },
}
</script>

<style scoped>
.ui-badge {
  position: relative;
  display: inline-flex;
}
.ui-badge__content {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
}
.ui-badge.is-dot .ui-badge__content {
  width: 8px;
  height: 8px;
  min-width: 8px;
  padding: 0;
  border-radius: 50%;
  top: 0;
  right: 0;
}
</style>
