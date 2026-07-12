<template>
  <svg
    class="meme-stat-icon"
    :class="`meme-stat-icon--${type}`"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <template v-if="type === 'views'">
      <rect x="3" y="5" width="18" height="14" rx="2.5" stroke="currentColor" stroke-width="1.75" />
      <path d="M10 9.5v5l4.5-2.5L10 9.5Z" fill="currentColor" stroke="none" />
    </template>
    <template v-else-if="type === 'likes'">
      <path
        d="M7 10.5V20M7 10.5 10.5 4.5a1.5 1.5 0 0 1 2.6-.9L14 10.5h4.5a2 2 0 0 1 1.98 2.35l-1.2 6A2 2 0 0 1 17.32 20H7"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>
    <template v-else>
      <path
        d="M7.5 8.5h9M7.5 12h6M7.5 15.5h4"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
      />
      <path
        d="M6 5.5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9.5L6 20.5V7.5a2 2 0 0 1 2-2Z"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linejoin="round"
      />
    </template>
  </svg>
</template>

<script>
export default {
  name: 'MemeStatIcon',
  props: {
    type: {
      type: String,
      required: true,
      validator: (value) => ['views', 'likes', 'comments'].includes(value),
    },
  },
}
</script>

<style scoped>
.meme-stat-icon {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
