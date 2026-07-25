<template>
  <a
    class="ui-link"
    :href="href"
    :target="target"
    :rel="rel"
    @click="$emit('click', $event)"
  >
    <slot />
  </a>
</template>

<script>
export default {
  name: 'UiLink',
  props: {
    href: { type: String, default: undefined },
    type: { type: String, default: 'primary' },
    underline: { type: [Boolean, String], default: true },
    target: { type: String, default: undefined },
    rel: { type: String, default: undefined },
  },
  emits: ['click'],
}
</script>

<style scoped>
.ui-link {
  color: var(--meme-primary);
  text-decoration: none;
  cursor: pointer;
}
.ui-link:hover {
  text-decoration: underline;
}
</style>
