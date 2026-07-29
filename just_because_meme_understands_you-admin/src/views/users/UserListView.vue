<template>
  <div>
    <div class="toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索昵称/签名"
        allow-clear
        style="width: 220px"
        @press-enter="reload"
      />
      <a-select
        v-model="status"
        placeholder="状态"
        allow-clear
        style="width: 120px"
        :options="[
          { label: '正常', value: 1 },
          { label: '禁用', value: 0 },
        ]"
      />
      <a-select
        v-model="role"
        placeholder="角色"
        allow-clear
        style="width: 150px"
        :options="[
          { label: 'ROLE_USER', value: 'ROLE_USER' },
          { label: 'ROLE_ADMIN', value: 'ROLE_ADMIN' },
        ]"
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
      <template #avatar="{ record }">
        <a-avatar v-if="record.avatar" :size="36">
          <img :src="record.avatar" alt="" />
        </a-avatar>
        <a-avatar v-else :size="36">{{ (record.nickname || '?').slice(0, 1) }}</a-avatar>
      </template>
      <template #status="{ record }">
        <a-tag :color="record.status === 1 ? 'green' : 'red'">
          {{ record.status === 1 ? '正常' : '禁用' }}
        </a-tag>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button type="text" size="mini" @click="toggleStatus(record)">
            {{ record.status === 1 ? '禁用' : '启用' }}
          </a-button>
          <a-button type="text" size="mini" @click="toggleRole(record)">
            {{ record.role === 'ROLE_ADMIN' ? '降为用户' : '升为管理员' }}
          </a-button>
        </a-space>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  fetchAdminUsers,
  updateAdminUserRole,
  updateAdminUserStatus,
} from '@/api/adminUser'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const loading = ref(false)
const list = ref([])
const keyword = ref('')
const status = ref(undefined)
const role = ref(undefined)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const columns = [
  { title: '头像', slotName: 'avatar', width: 72 },
  { title: 'ID', dataIndex: 'id', width: 160 },
  { title: '昵称', dataIndex: 'nickname', width: 140 },
  { title: '角色', dataIndex: 'role', width: 120 },
  { title: '状态', slotName: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', slotName: 'actions', width: 200 },
]

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminUsers({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      status: status.value,
      role: role.value,
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

async function toggleStatus(record) {
  try {
    const next = record.status === 1 ? 0 : 1
    await updateAdminUserStatus(record.id, next)
    toastSuccess('状态已更新')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function toggleRole(record) {
  try {
    const next = record.role === 'ROLE_ADMIN' ? 'ROLE_USER' : 'ROLE_ADMIN'
    await updateAdminUserRole(record.id, next)
    toastSuccess('角色已更新')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
