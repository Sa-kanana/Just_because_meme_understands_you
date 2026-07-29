<template>
  <div>
    <div class="toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索名称/介绍"
        allow-clear
        style="width: 220px"
        @press-enter="reload"
      />
      <a-select
        v-model="status"
        placeholder="状态"
        allow-clear
        style="width: 160px"
        :options="statusOptions"
      />
      <a-button type="primary" @click="reload">查询</a-button>
    </div>
    <a-table
      row-key="id"
      :loading="loading"
      :columns="columns"
      :data="list"
      :pagination="pagination"
      @page-change="onPageChange"
      @page-size-change="onPageSizeChange"
    >
      <template #image="{ record }">
        <a-image v-if="record.image" :src="record.image" width="48" height="48" />
      </template>
      <template #status="{ record }">
        <div>
          <div>{{ record.statusDesc }}</div>
          <div v-if="record.offlineReason" class="muted">{{ record.offlineReason }}</div>
          <div v-if="record.appealRejectCount" class="muted">驳回 {{ record.appealRejectCount }} 次</div>
        </div>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button
            v-if="record.status === 2 || record.status === 6"
            type="text"
            size="mini"
            status="success"
            @click="doApprove(record)"
          >
            通过
          </a-button>
          <a-button
            v-if="record.status === 2 || record.status === 6"
            type="text"
            size="mini"
            status="danger"
            @click="doReject(record)"
          >
            {{ record.status === 6 ? '驳回申诉' : '拒绝' }}
          </a-button>
          <a-button
            v-if="record.status === 1 || record.status === 2 || record.status === 3"
            type="text"
            size="mini"
            status="warning"
            @click="doOffline(record)"
          >
            风控下架
          </a-button>
          <a-button
            v-if="record.status === 3 || record.status === 5"
            type="text"
            size="mini"
            @click="doOnline(record)"
          >
            强制上架
          </a-button>
        </a-space>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  approveAdminMeme,
  fetchAdminMemes,
  offlineAdminMeme,
  onlineAdminMeme,
  rejectAdminMeme,
} from '@/api/adminMeme'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const route = useRoute()
const loading = ref(false)
const list = ref([])
const keyword = ref('')
const status = ref(undefined)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const statusOptions = [
  { label: '正常', value: 1 },
  { label: '审核中', value: 2 },
  { label: '主动下架', value: 3 },
  { label: '风控锁定', value: 5 },
  { label: '恢复审核中', value: 6 },
]

const columns = [
  { title: 'ID', dataIndex: 'id', width: 100 },
  { title: '封面', slotName: 'image', width: 72 },
  { title: '名称', dataIndex: 'name', ellipsis: true, tooltip: true },
  { title: '作者', dataIndex: 'authorNickname', width: 120 },
  { title: '状态', slotName: 'status', width: 140 },
  { title: '浏览', dataIndex: 'pageViews', width: 80 },
  { title: '更新时间', dataIndex: 'updateTime', width: 170 },
  { title: '操作', slotName: 'actions', width: 260 },
]

function applyRouteQuery() {
  const q = route.query || {}
  if (q.keyword != null && String(q.keyword).trim()) {
    keyword.value = String(q.keyword).trim()
  }
  if (q.status != null && String(q.status).trim() !== '') {
    const n = Number(q.status)
    status.value = Number.isFinite(n) ? n : undefined
  }
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminMemes({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      status: status.value,
    })
    list.value = data.list
    pagination.total = data.total
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  pagination.current = 1
  load()
}

function onPageChange(page) {
  pagination.current = page
  load()
}

function onPageSizeChange(size) {
  pagination.pageSize = size
  pagination.current = 1
  load()
}

async function doApprove(record) {
  try {
    await approveAdminMeme(record.id)
    toastSuccess('已通过')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doReject(record) {
  try {
    await rejectAdminMeme(record.id)
    toastSuccess(record.status === 6 ? '已驳回申诉' : '已拒绝')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doOffline(record) {
  try {
    await offlineAdminMeme(record.id)
    toastSuccess('已风控下架锁定')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doOnline(record) {
  try {
    await onlineAdminMeme(record.id)
    toastSuccess('已强制上架')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

watch(
  () => route.query,
  () => {
    applyRouteQuery()
    reload()
  }
)

onMounted(() => {
  applyRouteQuery()
  load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.muted {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
