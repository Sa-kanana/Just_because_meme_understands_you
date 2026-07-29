<template>
  <div>
    <div class="toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索标题/正文"
        allow-clear
        style="width: 220px"
        @press-enter="reload"
      />
      <a-input
        v-model="category"
        placeholder="分类"
        allow-clear
        style="width: 140px"
        @press-enter="reload"
      />
      <a-button type="primary" @click="reload">查询</a-button>
      <a-button @click="openCreate">手动传入</a-button>
      <a-button @click="openUpload">上传 MD / PDF</a-button>
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
      <template #tags="{ record }">
        <a-space wrap>
          <a-tag v-for="tag in record.tags || []" :key="tag">{{ tag }}</a-tag>
        </a-space>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
          <a-button type="text" size="mini" @click="doReindex(record)">重新灌库</a-button>
          <a-popconfirm content="删除后将从向量库移除，确认？" @ok="remove(record)">
            <a-button type="text" size="mini" status="danger">删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <a-modal
      v-model:visible="modalVisible"
      :title="editingId ? '编辑知识' : '手动传入知识'"
      width="720px"
      :ok-loading="saving"
      @ok="save"
      unmount-on-close
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model="form.title" maxlength="200" />
        </a-form-item>
        <a-form-item label="分类">
          <a-input v-model="form.category" maxlength="64" placeholder="如 FAQ / 玩法 / 规则" />
        </a-form-item>
        <a-form-item label="标签">
          <a-input v-model="tagsText" placeholder="逗号分隔，如：新手,审核" />
        </a-form-item>
        <a-form-item label="正文" required>
          <a-textarea
            v-model="form.content"
            :auto-size="{ minRows: 10, maxRows: 20 }"
            placeholder="支持较长文档，后端将按 LangChain 切块后写入 pgvector"
          />
        </a-form-item>
      </a-form>
      <p class="hint">保存后异步灌库；也可使用「上传 MD / PDF」由服务端提取文本。</p>
    </a-modal>

    <a-modal
      v-model:visible="uploadVisible"
      title="上传 Markdown / PDF"
      width="560px"
      :ok-loading="uploading"
      ok-text="上传并灌库"
      @ok="doUpload"
      unmount-on-close
    >
      <a-form :model="uploadForm" layout="vertical">
        <a-form-item label="文件" required>
          <a-upload
            :auto-upload="false"
            :limit="1"
            accept=".md,.markdown,.pdf,text/markdown,application/pdf"
            :file-list="fileList"
            @change="onFileChange"
            @remove="onFileRemove"
          >
            <template #upload-button>
              <a-button>选择文件</a-button>
            </template>
          </a-upload>
          <p class="hint">仅支持 .md / .markdown / .pdf，最大 15MB</p>
        </a-form-item>
        <a-form-item label="标题（可选，默认取文件名）">
          <a-input v-model="uploadForm.title" maxlength="200" />
        </a-form-item>
        <a-form-item label="分类">
          <a-input v-model="uploadForm.category" maxlength="64" />
        </a-form-item>
        <a-form-item label="标签">
          <a-input v-model="uploadTagsText" placeholder="逗号分隔" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createAdminKnowledge,
  deleteAdminKnowledge,
  fetchAdminKnowledge,
  reindexAdminKnowledge,
  updateAdminKnowledge,
  uploadAdminKnowledgeFile,
} from '@/api/adminKnowledge'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const list = ref([])
const keyword = ref('')
const category = ref('')
const modalVisible = ref(false)
const uploadVisible = ref(false)
const editingId = ref('')
const tagsText = ref('')
const uploadTagsText = ref('')
const fileList = ref([])
const selectedFile = ref(null)
const form = reactive({
  title: '',
  content: '',
  category: '',
})
const uploadForm = reactive({
  title: '',
  category: '',
})
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 160 },
  { title: '标题', dataIndex: 'title', ellipsis: true, tooltip: true },
  { title: '分类', dataIndex: 'category', width: 120 },
  { title: '标签', slotName: 'tags', width: 180 },
  { title: '更新时间', dataIndex: 'updateTime', width: 170 },
  { title: '操作', slotName: 'actions', width: 240 },
]

function parseTags(text) {
  return String(text || '')
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminKnowledge({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      category: category.value,
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
  form.title = ''
  form.content = ''
  form.category = ''
  tagsText.value = ''
  modalVisible.value = true
}

function openUpload() {
  uploadForm.title = ''
  uploadForm.category = ''
  uploadTagsText.value = ''
  fileList.value = []
  selectedFile.value = null
  uploadVisible.value = true
}

function openEdit(record) {
  editingId.value = String(record.id)
  form.title = record.title || ''
  form.content = record.content || ''
  form.category = record.category || ''
  tagsText.value = Array.isArray(record.tags) ? record.tags.join(',') : ''
  modalVisible.value = true
}

function onFileChange(fileItemList) {
  const items = Array.isArray(fileItemList) ? fileItemList : []
  fileList.value = items.slice(-1)
  const last = fileList.value[0]
  selectedFile.value = last && last.file ? last.file : null
  if (selectedFile.value && !uploadForm.title) {
    const name = String(selectedFile.value.name || '')
    uploadForm.title = name.replace(/\.(md|markdown|pdf)$/i, '')
  }
}

function onFileRemove() {
  fileList.value = []
  selectedFile.value = null
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    toastError('请填写标题和正文')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      category: form.category.trim(),
      tags: parseTags(tagsText.value),
    }
    if (editingId.value) {
      await updateAdminKnowledge(editingId.value, payload)
    } else {
      await createAdminKnowledge(payload)
    }
    toastSuccess('已保存并触发向量灌库')
    modalVisible.value = false
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function doUpload() {
  if (!selectedFile.value) {
    toastError('请选择 MD 或 PDF 文件')
    return
  }
  uploading.value = true
  try {
    await uploadAdminKnowledgeFile({
      file: selectedFile.value,
      title: uploadForm.title,
      category: uploadForm.category,
      tags: parseTags(uploadTagsText.value),
    })
    toastSuccess('上传成功并已触发向量灌库')
    uploadVisible.value = false
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function doReindex(record) {
  try {
    await reindexAdminKnowledge(record.id)
    toastSuccess('已触发重新灌库')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function remove(record) {
  try {
    await deleteAdminKnowledge(record.id)
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
.hint {
  margin: 8px 0 0;
  color: #86909c;
  font-size: 12px;
}
</style>
