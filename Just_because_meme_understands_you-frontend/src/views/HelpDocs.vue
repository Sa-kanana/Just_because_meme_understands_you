<template>
  <div class="page">
    <AppBreadcrumb :items="breadcrumbItems" />

    <el-card class="content-card" shadow="hover">
      <el-empty description="这里将展示网站帮助文档与说明（占位）" />
    </el-card>
  </div>
</template>

<script>
import AppBreadcrumb from '@/components/layout/AppBreadcrumb.vue'
import { buildHelpBreadcrumbs } from '@/utils/pageBreadcrumb'

export default {
  name: 'HelpDocsPage',
  components: {
    AppBreadcrumb,
  },
  computed: {
    breadcrumbItems() {
      return buildHelpBreadcrumbs()
    },
  },
}
</script>

<style scoped>
.page {
  padding: 24px 32px;
}

.content-card {
  border-radius: 12px;
}
</style>
