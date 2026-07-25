<template>
  <span style="display: none" />
</template>

<script>
export default {
  name: 'UiStep',
  inject: {
    uiStepsRegister: { default: null },
  },
  props: {
    title: { type: String, default: '' },
  },
  mounted() {
    if (typeof this.uiStepsRegister === 'function') {
      this.uiStepsRegister({ title: this.title })
    }
  },
}
</script>
