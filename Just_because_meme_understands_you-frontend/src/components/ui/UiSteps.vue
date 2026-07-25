<template>
  <div class="ui-steps">
    <div
      v-for="(step, index) in steps"
      :key="index"
      class="ui-steps__item"
      :class="{
        'is-active': index === active,
        'is-done': index < active,
      }"
    >
      <span class="ui-steps__index">{{ index + 1 }}</span>
      <span class="ui-steps__title">{{ step.title }}</span>
      <span v-if="index < steps.length - 1" class="ui-steps__line" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiSteps',
  props: {
    active: { type: Number, default: 0 },
  },
  data() {
    return { steps: [] }
  },
  provide() {
    return {
      uiStepsRegister: (step) => {
        this.steps.push(step)
      },
    }
  },
}
</script>

<style scoped>
.ui-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ui-steps__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--meme-text-muted);
  font-size: 13px;
}
.ui-steps__item.is-active,
.ui-steps__item.is-done {
  color: var(--meme-primary);
}
.ui-steps__index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  border: 1px solid currentColor;
}
.ui-steps__item.is-active .ui-steps__index,
.ui-steps__item.is-done .ui-steps__index {
  background: var(--meme-primary);
  color: #fff;
  border-color: var(--meme-primary);
}
.ui-steps__line {
  width: 28px;
  height: 1px;
  background: var(--meme-border);
}
</style>
