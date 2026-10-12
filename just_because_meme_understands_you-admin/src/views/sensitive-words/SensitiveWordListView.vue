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
        v-model="level"
        placeholder="风险等级"
        allow-clear
        style="width: 150px"
        :options="levelOptions"
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
      <template #riskLevel="{ record }">
        {{ levelLabel(record.riskLevel) }}
      </template>
      <template #replacement="{ record }">
        {{ record.replacement || '—' }}
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
        <a-form-item label="风险等级" required>
          <a-select
            v-model="form.level"
            :options="[
              { label: '普通：允许或替换', value: 'NORMAL' },
              { label: '需要人工审核', value: 'REVIEW' },
              { label: '直接拒绝', value: 'BLOCK' },
            ]"
          />
        </a-form-item>
        <p class="policy-hint">
          梗发布和修改稿命中“需要人工审核”会进入审核队列；评论、昵称和签名没有待审队列，会拒绝本次提交。
        </p>
        <a-form-item v-if="form.level === 'NORMAL'" label="替换文本">
          <a-input v-model="form.replacement" maxlength="20" placeholder="留空表示允许原文通过" />
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
const level = ref(undefined)
const status = ref(undefined)
const modalVisible = ref(false)
const editingId = ref('')
const form = reactive({
  word: '',
  category: 'CUSTOM',
  level: 'NORMAL',
  replacement: '***',
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
const levelOptions = [
  { label: '普通', value: 'NORMAL' },
  { label: '人工审核', value: 'REVIEW' },
  { label: '直接拒绝', value: 'BLOCK' },
]

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '词语', dataIndex: 'word' },
  { title: '分类', dataIndex: 'category', width: 110 },
  { title: '风险等级', slotName: 'riskLevel', width: 140 },
  { title: '替换文本', slotName: 'replacement', width: 130 },
  { title: '状态', slotName: 'status', width: 90 },
  { title: '创建人', dataIndex: 'creator', width: 120 },
  { title: '操作', slotName: 'actions', width: 220 },
]

function levelLabel(level) {
  if (level === 'NORMAL') return '普通 / 替换'
  if (level === 'REVIEW') return '人工审核'
  if (level === 'BLOCK') return '直接拒绝'
  return String(level ?? '')
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminSensitiveWords({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      category: category.value,
      level: level.value,
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
  form.level = 'NORMAL'
  form.replacement = '***'
  form.status = 1
  modalVisible.value = true
}

function openEdit(record) {
  editingId.value = String(record.id)
  form.word = record.word || ''
  form.category = record.category || 'CUSTOM'
  form.level = record.riskLevel || 'NORMAL'
  form.replacement = record.replacement || ''
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
      level: form.level,
      replacement: form.level === 'NORMAL' ? form.replacement.trim() : '',
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

.policy-hint {
  margin: -8px 0 16px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}
</style>
