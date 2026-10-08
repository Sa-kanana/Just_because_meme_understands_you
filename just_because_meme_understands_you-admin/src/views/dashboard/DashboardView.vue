<template>
  <div>
    <div class="dashboard-stats">
      <a-card v-for="item in cards" :key="item.label" :loading="loading">
        <a-statistic :title="item.label" :value="item.value" />
      </a-card>
    </div>
    <p class="hint">数据来自分页接口 total，点击侧栏进入对应管理页。</p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchAdminMemes } from '@/api/adminMeme'
import { fetchAdminUsers } from '@/api/adminUser'
import { fetchAdminHomeImages } from '@/api/adminHomeImage'
import { fetchAdminTags } from '@/api/adminTag'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError } from '@/utils/uiFeedback'

const loading = ref(false)
const cards = ref([
  { label: '待审梗', value: 0 },
  { label: '用户总数', value: 0 },
  { label: '轮播图', value: 0 },
  { label: '标签数', value: 0 },
])

onMounted(async () => {
  loading.value = true
  try {
    const [reviewing, users, images, tags] = await Promise.all([
      fetchAdminMemes({ page: 1, size: 1, status: 2 }),
      fetchAdminUsers({ page: 1, size: 1 }),
      fetchAdminHomeImages({ page: 1, size: 1 }),
      fetchAdminTags({ page: 1, size: 1 }),
    ])
    cards.value = [
      { label: '待审梗', value: reviewing.total },
      { label: '用户总数', value: users.total },
      { label: '轮播图', value: images.total },
      { label: '标签数', value: tags.total },
    ]
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '加载概览失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dashboard-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.hint {
  margin-top: 16px;
  color: #86909c;
  font-size: 13px;
}

@media (max-width: 960px) {
  .dashboard-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 380px) {
  .dashboard-stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
