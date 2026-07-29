<template>
  <div>
    <div class="toolbar">
      <a-select
        v-model="status"
        placeholder="状态"
        allow-clear
        style="width: 120px"
        :options="[
          { label: '上线', value: 1 },
          { label: '下线', value: 0 },
        ]"
      />
      <a-button type="primary" @click="reload">查询</a-button>
      <a-button @click="openCreate">新增轮播</a-button>
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
      <template #img="{ record }">
        <a-image v-if="record.imgUrl" :src="record.imgUrl" width="72" height="40" />
      </template>
      <template #status="{ record }">
        <a-tag :color="record.status === 1 ? 'green' : 'gray'">
          {{ record.status === 1 ? '上线' : '下线' }}
        </a-tag>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
          <a-button type="text" size="mini" @click="toggleStatus(record)">
            {{ record.status === 1 ? '下线' : '上线' }}
          </a-button>
          <a-popconfirm content="确认删除？" @ok="remove(record)">
            <a-button type="text" size="mini" status="danger">删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <a-modal
      v-model:visible="modalVisible"
      :title="editingId ? '编辑轮播' : '新增轮播'"
      :ok-loading="saving"
      @ok="save"
      @cancel="modalVisible = false"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="标题">
          <a-input v-model="form.title" />
        </a-form-item>
        <a-form-item label="图片" required>
          <div class="upload-row">
            <a-input v-model="form.imgUrl" placeholder="图片 URL / OSS key" />
            <a-upload :custom-request="onUpload" :show-file-list="false" accept="image/*">
              <template #upload-button>
                <a-button>上传</a-button>
              </template>
            </a-upload>
          </div>
          <a-image v-if="form.imgUrl" :src="form.imgUrl" width="120" style="margin-top: 8px" />
        </a-form-item>
        <a-form-item label="跳转类型" required>
          <a-select
            v-model="form.targetType"
            :options="[
              { label: '无跳转', value: 0 },
              { label: '梗 ID', value: 1 },
              { label: '外链', value: 2 },
            ]"
          />
        </a-form-item>
        <a-form-item v-if="form.targetType !== 0" label="跳转目标">
          <a-input v-model="form.targetValue" />
        </a-form-item>
        <a-form-item label="排序权重">
          <a-input-number v-model="form.sortOrder" :min="0" />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model="form.status">
            <a-radio :value="1">上线</a-radio>
            <a-radio :value="0">下线</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createAdminHomeImage,
  deleteAdminHomeImage,
  fetchAdminHomeImages,
  updateAdminHomeImage,
  updateAdminHomeImageStatus,
} from '@/api/adminHomeImage'
import { uploadToOss } from '@/api/oss'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const status = ref(undefined)
const modalVisible = ref(false)
const editingId = ref('')
const form = reactive({
  title: '',
  imgUrl: '',
  targetType: 0,
  targetValue: '',
  sortOrder: 0,
  status: 1,
})
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '预览', slotName: 'img', width: 90 },
  { title: '标题', dataIndex: 'title' },
  { title: '排序', dataIndex: 'sortOrder', width: 80 },
  { title: '状态', slotName: 'status', width: 90 },
  { title: '操作', slotName: 'actions', width: 220 },
]

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminHomeImages({
      page: pagination.current,
      size: pagination.pageSize,
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

function resetForm() {
  form.title = ''
  form.imgUrl = ''
  form.targetType = 0
  form.targetValue = ''
  form.sortOrder = 0
  form.status = 1
}

function openCreate() {
  editingId.value = ''
  resetForm()
  modalVisible.value = true
}

function openEdit(record) {
  editingId.value = String(record.id)
  form.title = record.title || ''
  form.imgUrl = record.imgUrl || ''
  form.targetType = record.targetType ?? 0
  form.targetValue = record.targetValue || ''
  form.sortOrder = record.sortOrder ?? 0
  form.status = record.status ?? 1
  modalVisible.value = true
}

async function onUpload(option) {
  try {
    const url = await uploadToOss(option.fileItem.file, 'home')
    form.imgUrl = url
    option.onSuccess()
    toastSuccess('上传成功')
  } catch (e) {
    option.onError(e)
    toastError(e.message || '上传失败')
  }
}

async function save() {
  if (!form.imgUrl) {
    toastError('请上传图片')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title,
      imgUrl: form.imgUrl,
      targetType: form.targetType,
      targetValue: form.targetValue,
      sortOrder: form.sortOrder,
      status: form.status,
    }
    if (editingId.value) {
      await updateAdminHomeImage(editingId.value, payload)
    } else {
      await createAdminHomeImage(payload)
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
    await updateAdminHomeImageStatus(record.id, record.status === 1 ? 0 : 1)
    toastSuccess('状态已更新')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function remove(record) {
  try {
    await deleteAdminHomeImage(record.id)
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
.upload-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
