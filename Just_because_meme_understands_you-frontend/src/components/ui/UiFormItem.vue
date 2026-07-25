<template>
  <div class="ui-form-item">
    <label v-if="label" class="ui-form-item__label">
      {{ label }}
      <span v-if="required" class="ui-form-item__req">*</span>
    </label>
    <div class="ui-form-item__content">
      <slot />
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiFormItem',
  props: {
    label: { type: String, default: '' },
    prop: { type: String, default: '' },
    required: { type: Boolean, default: false },
  },
}
</script>

<style scoped>
.ui-form-item__label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--meme-text);
}
.ui-form-item__req {
  color: var(--meme-danger, #e5484d);
  margin-left: 2px;
}
.ui-form-item__content {
  min-width: 0;
}
</style>
