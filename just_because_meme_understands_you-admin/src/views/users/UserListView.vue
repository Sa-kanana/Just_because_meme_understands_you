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
          { label: '普通用户', value: 'ROLE_USER' },
          { label: '管理员', value: 'ROLE_ADMIN' },
          { label: '超级管理员', value: 'ROLE_SUPER_ADMIN' },
        ]"
      />
      <a-select
        v-if="isSuperAdminOperator"
        v-model="deletedOnly"
        placeholder="账号范围"
        style="width: 150px"
        :options="[
          { label: '正常账号', value: false },
          { label: '已注销账号', value: true },
        ]"
        @change="reload"
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
        <a-tag :color="record.deletedAt ? 'gray' : record.status === 1 ? 'green' : 'red'">
          {{ record.deletedAt ? '已注销' : record.status === 1 ? '正常' : '禁用' }}
        </a-tag>
      </template>
      <template #role="{ record }">
        <a-tag :color="roleTagColor(record.role)">{{ roleLabel(record.role) }}</a-tag>
      </template>
      <template #actions="{ record }">
        <a-space v-if="record.deletedAt">
          <a-button type="text" size="mini" @click="restoreUser(record)">恢复账号</a-button>
        </a-space>
        <a-space v-else>
          <a-button type="text" size="mini" @click="toggleStatus(record)">
            {{ record.status === 1 ? '禁用' : '启用' }}
          </a-button>
          <a-select
            v-if="canEditRole(record)"
            :model-value="record.role"
            :options="roleOptions"
            size="mini"
            style="width: 132px"
            @change="(value) => changeRole(record, value)"
          />
          <span v-else class="role-readonly">权限受限</span>
          <a-popconfirm
            v-if="canDelete(record)"
            :content="deleteConfirmText(record)"
            ok-text="确认删除"
            cancel-text="取消"
            @ok="deleteUser(record)"
          >
            <a-button type="text" status="danger" size="mini">删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deleteAdminUser,
  fetchAdminUsers,
  restoreAdminUser,
  updateAdminUserRole,
  updateAdminUserStatus,
} from '@/api/adminUser'
import { useAuthStore } from '@/stores/auth'
import { isAuthErrorHandled } from '@/utils/authSession'
import { canDeleteUser, canEditUserRole, getEditableUserRoleOptions, isSuperAdmin } from '@/utils/adminUserRoles.mjs'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const auth = useAuthStore()
const loading = ref(false)
const list = ref([])
const keyword = ref('')
const status = ref(undefined)
const role = ref(undefined)
const deletedOnly = ref(false)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const roleOptions = computed(() => getEditableUserRoleOptions(auth.user))
const isSuperAdminOperator = computed(() => isSuperAdmin(auth.user))

const roleLabels = {
  ROLE_USER: '普通用户',
  ROLE_ADMIN: '管理员',
  ROLE_SUPER_ADMIN: '超级管理员',
}

const columns = [
  { title: '头像', slotName: 'avatar', width: 72 },
  { title: 'ID', dataIndex: 'id', width: 160 },
  { title: '昵称', dataIndex: 'nickname', width: 140 },
  { title: '角色', slotName: 'role', width: 120 },
  { title: '状态', slotName: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '注销时间', dataIndex: 'deletedAt', width: 170 },
  { title: '操作', slotName: 'actions', width: 260 },
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
      deleted: deletedOnly.value,
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

function canEditRole(record) {
  return canEditUserRole(auth.user, record)
}

function canDelete(record) {
  return canDeleteUser(auth.user, record)
}

function deleteConfirmText(record) {
  return `确认注销用户「${record.nickname || '未命名用户'}」（ID：${record.id}）？该账号将无法继续登录。`
}

function roleLabel(roleCode) {
  return roleLabels[roleCode] || '普通用户'
}

function roleTagColor(roleCode) {
  if (roleCode === 'ROLE_SUPER_ADMIN') return 'orange'
  if (roleCode === 'ROLE_ADMIN') return 'arcoblue'
  return 'gray'
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

async function changeRole(record, nextRole) {
  if (!nextRole || nextRole === record.role) return
  try {
    await updateAdminUserRole(record.id, nextRole)
    toastSuccess('角色已更新')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function deleteUser(record) {
  try {
    await deleteAdminUser(record.id)
    toastSuccess('账号已注销，历史梗图和评论仍会保留')
    if (list.value.length === 1 && pagination.current > 1) pagination.current -= 1
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '删除失败')
  }
}

async function restoreUser(record) {
  try {
    await restoreAdminUser(record.id)
    toastSuccess('账号已恢复；原登录状态不会恢复，请重新登录')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '恢复失败')
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
