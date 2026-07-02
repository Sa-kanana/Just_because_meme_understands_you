<template>
  <div class="page">
    <el-breadcrumb class="page-breadcrumb" separator=">">
      <el-breadcrumb-item>
        <router-link to="/" class="crumb-link">主页</router-link>
      </el-breadcrumb-item>
      <el-breadcrumb-item>帮助文档</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card" shadow="hover">
      <el-empty description="这里将展示网站帮助文档与说明（占位）" />
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'HelpDocsPage',
}
</script>

<style scoped>
.page {
  padding: 24px 32px;
}

.page-breadcrumb {
  margin-bottom: 16px;
}

.crumb-link {
  display: inline-block;
  padding: 2px 4px;
  border-radius: 4px;
  text-decoration: none;
  color: #6b7280;
  transition: color 0.15s ease, background-color 0.15s ease, transform 0.05s ease;
}

.crumb-link:hover {
  color: #111827;
  background-color: #f3f4f6;
  text-decoration: none;
}

.crumb-link:active {
  color: #111827;
  background-color: #e5e7eb;
  transform: scale(0.97);
}

.crumb-link:focus-visible {
  outline: 2px solid #318AEF;
  outline-offset: 2px;
}

.crumb-link:link,
.crumb-link:visited {
  color: #6b7280;
}

.content-card {
  border-radius: 12px;
}
</style>

