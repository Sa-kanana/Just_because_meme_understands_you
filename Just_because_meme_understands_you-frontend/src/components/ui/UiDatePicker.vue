<template>
  <input
    class="ui-date-picker"
    type="date"
    :value="modelValue"
    :disabled="disabled"
    :placeholder="placeholder"
    @input="$emit('update:modelValue', $event.target.value)"
  />
</template>

<script>
export default {
  name: 'UiDatePicker',
  props: {
    modelValue: { type: String, default: '' },
    type: { type: String, default: 'date' },
    placeholder: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    valueFormat: { type: String, default: 'YYYY-MM-DD' },
  },
  emits: ['update:modelValue'],
}
</script>

<style scoped>
.ui-date-picker {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-sm, 8px);
  background: var(--meme-bg-card);
  color: var(--meme-text);
  font-size: 14px;
}
</style>
