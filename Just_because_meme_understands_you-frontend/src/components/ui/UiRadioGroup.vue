<template>
  <div class="ui-radio-group">
    <slot />
  </div>
</template>

<script>
export default {
  name: 'UiRadioGroup',
  props: {
    modelValue: { type: [String, Number, Boolean], default: undefined },
    size: { type: String, default: 'default' },
  },
  emits: ['update:modelValue'],
  provide() {
    return {
      uiRadioGroupValue: () => this.modelValue,
      uiRadioGroupUpdate: (val) => this.$emit('update:modelValue', val),
    }
  },
}
</script>

<style scoped>
.ui-radio-group {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
