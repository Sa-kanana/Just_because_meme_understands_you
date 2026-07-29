<template>
  <div>
    <div class="toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索敏感词"
        allow-clear
        style="width: 180px"
        @press-enter="reload"
      />
      <a-select
        v-model="category"
        placeholder="分类"
        allow-clear
        style="width: 140px"
        :options="categoryOptions"
      />
      <a-select
        v-model="status"
        placeholder="状态"
        allow-clear
        style="width: 120px"
        :options="[
          { label: '启用', value: 1 },
          { label: '禁用', value: 0 },
        ]"
      />
      <a-button type="primary" @click="reload">查询</a-button>
      <a-button @click="openCreate">新增</a-button>
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
      <template #actionType="{ record }">
        {{ actionLabel(record.actionType) }}
      </template>
      <template #status="{ record }">
        <a-tag :color="record.status === 1 ? 'green' : 'gray'">
          {{ record.status === 1 ? '启用' : '禁用' }}
        </a-tag>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
          <a-button type="text" size="mini" @click="toggleStatus(record)">
            {{ record.status === 1 ? '禁用' : '启用' }}
          </a-button>
          <a-popconfirm content="确认删除？" @ok="remove(record)">
            <a-button type="text" size="mini" status="danger">删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <a-modal
      v-model:visible="modalVisible"
      :title="editingId ? '编辑敏感词' : '新增敏感词'"
      :ok-loading="saving"
      @ok="save"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="词语" required>
          <a-input v-model="form.word" maxlength="64" />
        </a-form-item>
        <a-form-item label="分类" required>
          <a-select v-model="form.category" :options="categoryOptions" />
        </a-form-item>
        <a-form-item label="处理策略" required>
          <a-select
            v-model="form.actionType"
            :options="[
              { label: '替换 ***', value: 1 },
              { label: '人工审核', value: 2 },
              { label: '直接拒绝', value: 3 },
            ]"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createAdminSensitiveWord,
  deleteAdminSensitiveWord,
  fetchAdminSensitiveWords,
  updateAdminSensitiveWord,
  updateAdminSensitiveWordStatus,
} from '@/api/adminSensitiveWord'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const keyword = ref('')
const category = ref(undefined)
const status = ref(undefined)
const modalVisible = ref(false)
const editingId = ref('')
const form = reactive({
  word: '',
  category: 'CUSTOM',
  actionType: 1,
  status: 1,
})
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const categoryOptions = [
  { label: 'POLITICS', value: 'POLITICS' },
  { label: 'PORN', value: 'PORN' },
  { label: 'ABUSE', value: 'ABUSE' },
  { label: 'AD', value: 'AD' },
  { label: 'CUSTOM', value: 'CUSTOM' },
]

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '词语', dataIndex: 'word' },
  { title: '分类', dataIndex: 'category', width: 110 },
  { title: '策略', slotName: 'actionType', width: 110 },
  { title: '状态', slotName: 'status', width: 90 },
  { title: '创建人', dataIndex: 'creator', width: 120 },
  { title: '操作', slotName: 'actions', width: 220 },
]

function actionLabel(type) {
  if (type === 1) return '替换'
  if (type === 2) return '审核'
  if (type === 3) return '拒绝'
  return String(type ?? '')
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminSensitiveWords({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      category: category.value,
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

function openCreate() {
  editingId.value = ''
  form.word = ''
  form.category = 'CUSTOM'
  form.actionType = 1
  form.status = 1
  modalVisible.value = true
}

function openEdit(record) {
  editingId.value = String(record.id)
  form.word = record.word || ''
  form.category = record.category || 'CUSTOM'
  form.actionType = record.actionType ?? 1
  form.status = record.status ?? 1
  modalVisible.value = true
}

async function save() {
  if (!form.word.trim()) {
    toastError('请输入敏感词')
    return
  }
  saving.value = true
  try {
    const payload = {
      word: form.word.trim(),
      category: form.category,
      actionType: form.actionType,
      status: form.status,
    }
    if (editingId.value) {
      await updateAdminSensitiveWord(editingId.value, payload)
    } else {
      await createAdminSensitiveWord(payload)
    }
    toastSuccess('已保存')
    modalVisible.value = false
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(record) {
  try {
    await updateAdminSensitiveWordStatus(record.id, record.status === 1 ? 0 : 1)
    toastSuccess('状态已更新')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function remove(record) {
  try {
    await deleteAdminSensitiveWord(record.id)
    toastSuccess('已删除')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '删除失败')
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
