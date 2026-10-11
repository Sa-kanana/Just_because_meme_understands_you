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
    <div v-if="selectedKeys.length" class="batch-toolbar">
      <span class="batch-selection">已选 {{ selectedRecords.length }} 条</span>
      <a-popconfirm
        :content="`删除选中的 ${selectedRecords.length} 个标签，并清理关联关系，确认继续？`"
        @ok="removeSelected"
      >
        <a-button size="small" status="danger" :disabled="batching || !selectedRecords.length">
          批量删除 {{ selectedRecords.length }}
        </a-button>
      </a-popconfirm>
      <a-button size="small" type="text" :disabled="batching" @click="selectedKeys = []">清除选择</a-button>
    </div>
    <a-table
      row-key="id"
      v-model:selectedKeys="selectedKeys"
      :row-selection="rowSelection"
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
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createAdminTag,
  deleteAdminTag,
  fetchAdminTags,
  updateAdminTag,
} from '@/api/adminTag'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'
import { runBatchOperations } from '@/utils/batchOperations.mjs'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const selectedKeys = ref([])
const batching = ref(false)
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

const rowSelection = { type: 'checkbox', showCheckedAll: true, onlyCurrent: true }
const selectedRecords = computed(() => {
  const selectedIds = new Set(selectedKeys.value.map(String))
  return list.value.filter((record) => selectedIds.has(String(record.id)))
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '名称', dataIndex: 'name' },
  { title: '关联数量', dataIndex: 'relatedQuantity', width: 120 },
  { title: '操作', slotName: 'actions', width: 160 },
]

async function load() {
  selectedKeys.value = []
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

async function removeSelected() {
  if (batching.value || !selectedRecords.value.length) return
  batching.value = true
  try {
    const result = await runBatchOperations(selectedRecords.value, (record) => deleteAdminTag(record.id))
    if (result.failed.length === 0) {
      toastSuccess(`已删除 ${result.succeeded.length} 个标签`)
    } else {
      const reason = result.failed[0].error && result.failed[0].error.message
      const message = `批量删除完成：成功 ${result.succeeded.length} 个，失败 ${result.failed.length} 个${reason ? `（${reason}）` : ''}`
      if (!isAuthErrorHandled(result.failed[0].error)) toastError(message)
    }
    await load()
  } finally {
    batching.value = false
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
.batch-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 14px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}
.batch-selection { margin-right: 4px; color: var(--color-text-2); font-size: 13px; }
</style>
