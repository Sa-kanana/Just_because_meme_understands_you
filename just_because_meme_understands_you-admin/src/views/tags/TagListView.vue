<template>
  <div>
    <div class="toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索标签"
        allow-clear
        style="width: 220px"
        @press-enter="reload"
      />
      <a-button type="primary" @click="reload">查询</a-button>
      <a-button @click="openCreate">新增标签</a-button>
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
      <template #actions="{ record }">
        <a-space>
          <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
          <a-popconfirm content="删除会同时清理关联关系，确认？" @ok="remove(record)">
            <a-button type="text" size="mini" status="danger">删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <a-modal
      v-model:visible="modalVisible"
      :title="editingId ? '编辑标签' : '新增标签'"
      :ok-loading="saving"
      @ok="save"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="标签名" required>
          <a-input v-model="form.name" maxlength="50" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createAdminTag,
  deleteAdminTag,
  fetchAdminTags,
  updateAdminTag,
} from '@/api/adminTag'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const keyword = ref('')
const modalVisible = ref(false)
const editingId = ref('')
const form = reactive({ name: '' })
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '名称', dataIndex: 'name' },
  { title: '关联数量', dataIndex: 'relatedQuantity', width: 120 },
  { title: '操作', slotName: 'actions', width: 160 },
]

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminTags({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
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
  form.name = ''
  modalVisible.value = true
}

function openEdit(record) {
  editingId.value = String(record.id)
  form.name = record.name || ''
  modalVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    toastError('请输入标签名')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAdminTag(editingId.value, { name: form.name.trim() })
    } else {
      await createAdminTag({ name: form.name.trim() })
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

async function remove(record) {
  try {
    await deleteAdminTag(record.id)
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
