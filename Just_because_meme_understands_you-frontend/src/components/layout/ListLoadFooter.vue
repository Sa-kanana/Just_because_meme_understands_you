<template>
  <div v-if="visible" class="list-load-footer">
    <el-button
      v-if="hasMore"
      round
      :loading="loading"
      :disabled="loading"
      @click="$emit('load-more')"
    >
      {{ loading ? '加载中…' : '加载更多' }}
    </el-button>
    <p v-else class="list-load-footer__end">{{ endText }}</p>
  </div>
</template>

<script>
export default {
  name: 'ListLoadFooter',
  props: {
    hasMore: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    itemCount: {
      type: Number,
      default: 0,
    },
    endText: {
      type: String,
      default: '没有更多了',
    },
  },
  emits: ['load-more'],
  computed: {
    visible() {
      return this.itemCount > 0
    },
  },
}
</script>

<style scoped>
.list-load-footer {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}

.list-load-footer__end {
  margin: 0;
  padding: 8px 0 4px;
  font-size: 13px;
  color: var(--meme-text-muted);
  letter-spacing: 0.02em;
}
</style>
