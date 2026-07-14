<template>
  <div v-if="items.length" class="global-breadcrumb">
    <div class="global-breadcrumb__inner">
      <AppBreadcrumb :items="items" />
    </div>
  </div>
</template>

<script>
import AppBreadcrumb from '@/components/layout/AppBreadcrumb.vue'
import { useAuthStore } from '@/stores/auth'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import { resolveRouteBreadcrumbs } from '@/utils/pageBreadcrumb'

export default {
  name: 'GlobalBreadcrumb',
  components: { AppBreadcrumb },
  computed: {
    items() {
      return resolveRouteBreadcrumbs(this.$route, {
        authStore: useAuthStore(),
        patch: useBreadcrumbStore().patch,
      })
    },
  },
}
</script>

<style scoped>
.global-breadcrumb {
  width: 100%;
}

.global-breadcrumb__inner {
  width: 100%;
  padding: 0 0 12px;
}

.global-breadcrumb__inner :deep(.app-breadcrumb) {
  margin-bottom: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--meme-border);
}

@media (max-width: 640px) {
  .global-breadcrumb__inner {
    padding-bottom: 10px;
  }
}
</style>
