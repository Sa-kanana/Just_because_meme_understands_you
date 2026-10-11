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
    <div v-if="selectedKeys.length" class="batch-toolbar">
      <span class="batch-selection">已选 {{ selectedRecords.length }} 条</span>
      <a-popconfirm
        :content="`将为选中的 ${selectedRecords.length} 篇知识触发重新灌库，确认继续？`"
        @ok="reindexSelected"
      >
        <a-button size="small" type="primary" :disabled="batching || !selectedRecords.length">
          批量重新灌库 {{ selectedRecords.length }}
        </a-button>
      </a-popconfirm>
      <a-popconfirm
        :content="`删除选中的 ${selectedRecords.length} 篇知识，并从向量库移除，确认继续？`"
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
      <template #tags="{ record }">
        <a-space wrap>
          <a-tag v-for="tag in record.tags || []" :key="tag">{{ tag }}</a-tag>
        </a-space>
      </template>
      <template #indexStatus="{ record }">
        <a-tooltip
          v-if="record.indexError && ['PENDING', 'DELETE_PENDING', 'FAILED'].includes(record.indexStatus)"
          :content="indexStatusTooltip(record)"
        >
          <a-tag :color="indexStatusColor(record.indexStatus, record.indexError)">
            {{ indexStatusLabel(record.indexStatus, record.indexError) }}
          </a-tag>
        </a-tooltip>
        <a-tag v-else :color="indexStatusColor(record.indexStatus, record.indexError)">
          {{ indexStatusLabel(record.indexStatus, record.indexError) }}
        </a-tag>
      </template>
      <template #actions="{ record }">
        <a-space>
          <template v-if="record.status === 0">
            <a-button
              type="text"
              size="mini"
              status="danger"
              :disabled="record.indexStatus !== 'FAILED'"
              @click="remove(record)"
            >
              {{ record.indexStatus === 'FAILED' ? '重试向量清理' : '清理中' }}
            </a-button>
          </template>
          <template v-else>
            <a-button type="text" size="mini" @click="openEdit(record)">编辑</a-button>
            <a-button type="text" size="mini" @click="doReindex(record)">重新灌库</a-button>
            <a-popconfirm content="删除后将从向量库移除，确认？" @ok="remove(record)">
              <a-button type="text" size="mini" status="danger">删除</a-button>
            </a-popconfirm>
          </template>
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
      :ok-text="uploading ? '逐份上传中' : '上传并灌库'"
      @ok="doUpload"
      unmount-on-close
    >
      <a-form :model="uploadForm" layout="vertical">
        <a-form-item label="文件" required>
          <a-upload
            class="knowledge-file-upload"
            :auto-upload="false"
            :multiple="true"
            :limit="MAX_KNOWLEDGE_UPLOAD_FILES"
            accept=".md,.markdown,.pdf,text/markdown,application/pdf"
            :file-list="fileList"
            @change="onFileChange"
            @remove="onFileRemove"
          >
            <template #upload-button>
              <a-button>选择文件</a-button>
            </template>
          </a-upload>
          <p class="hint">支持一次选择最多 20 份 MD / PDF；单份最大 25MB，正文最多 100 万字符。每次最多并行提交 2 份。</p>
        </a-form-item>
        <a-form-item v-if="selectedFiles.length <= 1" label="标题（可选，默认取文件名）">
          <a-input v-model="uploadForm.title" maxlength="200" />
        </a-form-item>
        <a-form-item label="分类">
          <a-input v-model="uploadForm.category" maxlength="64" />
        </a-form-item>
        <a-form-item label="标签">
          <a-input v-model="uploadTagsText" placeholder="逗号分隔" />
        </a-form-item>
      </a-form>
      <div v-if="uploadQueue.length" class="upload-results">
        <div v-for="item in uploadQueue" :key="item.id" class="upload-result-row">
          <div class="upload-result-heading">
            <span class="upload-result-name" :title="item.name">{{ item.name }}</span>
            <a-tag :color="item.state === 'failed' ? 'red' : item.state === 'done' ? 'green' : 'arcoblue'">
              {{ item.status }}
            </a-tag>
          </div>
          <p v-if="item.error" class="upload-result-error">{{ item.error }}</p>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  createAdminKnowledge,
  deleteAdminKnowledge,
  fetchAdminKnowledge,
  fetchAdminKnowledgeTask,
  reindexAdminKnowledge,
  updateAdminKnowledge,
  uploadAdminKnowledgeFile,
} from '@/api/adminKnowledge'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'
import { runBatchOperations } from '@/utils/batchOperations.mjs'

// 中文注释：须与后端知识上传上限保持一致，客户端先拦截明显过大的文件。
const MAX_KNOWLEDGE_FILE_BYTES = 25 * 1024 * 1024
// 中文注释：一次最多选择 20 份文件，避免单次任务队列无限增长。
const MAX_KNOWLEDGE_UPLOAD_FILES = 20
// 中文注释：浏览器端最多同时上传 2 份，控制网络及后端解析内存峰值。
const KNOWLEDGE_UPLOAD_CONCURRENCY = 2

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const list = ref([])
const selectedKeys = ref([])
const batching = ref(false)
const keyword = ref('')
const category = ref('')
const modalVisible = ref(false)
const uploadVisible = ref(false)
const editingId = ref('')
const tagsText = ref('')
const uploadTagsText = ref('')
const fileList = ref([])
const selectedFiles = ref([])
const uploadQueue = ref([])
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
let pollingTimer = null
let viewMounted = false
const notifiedFailedIndexTasks = new Set()

const rowSelection = { type: 'checkbox', showCheckedAll: true, onlyCurrent: true }
const selectedRecords = computed(() => {
  const selectedIds = new Set(selectedKeys.value.map(String))
  return list.value.filter((record) => record.status === 1 && selectedIds.has(String(record.id)))
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 160 },
  { title: '标题', dataIndex: 'title', ellipsis: true, tooltip: true },
  { title: '分类', dataIndex: 'category', width: 120 },
  { title: '标签', slotName: 'tags', width: 180 },
  { title: '向量状态', slotName: 'indexStatus', width: 120 },
  { title: '更新时间', dataIndex: 'updateTime', width: 170 },
  { title: '操作', slotName: 'actions', width: 240 },
]

function parseTags(text) {
  return String(text || '')
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function load({ silent = false } = {}) {
  if (!silent) selectedKeys.value = []
  if (!silent) loading.value = true
  try {
    const data = await fetchAdminKnowledge({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      category: category.value,
    })
    list.value = data.list.map((record) => ({ ...record, disabled: record.status === 0 }))
    pagination.total = data.total
    const newlyFailed = list.value.filter(notifyFailedIndexTask)
    if (newlyFailed.length) {
      const firstSummary = formatFailedIndexSummary(newlyFailed[0])
      toastError(newlyFailed.length === 1
        ? firstSummary
        : `有 ${newlyFailed.length} 个知识索引任务最终失败。${firstSummary}`)
    }
  } catch (e) {
    if (!silent && !isAuthErrorHandled(e)) toastError(e.message || '加载失败')
  } finally {
    if (!silent) loading.value = false
  }
}

function indexStatusLabel(status, error) {
  if (status === 'PENDING' && error) return '重试中'
  if (status === 'DELETE_PENDING' && error) return '删除重试中'
  return ({
    PENDING: '排队中',
    PROCESSING: '索引中',
    READY: '可检索',
    FAILED: '处理失败',
    DELETE_PENDING: '删除中',
    DELETED: '已移除',
  })[status] || '未知'
}

function indexStatusColor(status, error) {
  if (error && ['PENDING', 'DELETE_PENDING'].includes(status)) return 'orange'
  return ({ READY: 'green', FAILED: 'red', DELETE_PENDING: 'orange', PROCESSING: 'arcoblue' })[status] || 'gray'
}

function indexStatusTooltip(record) {
  const summary = String(record.indexError || '').slice(0, 1000)
  if (record.indexStatus === 'FAILED') return summary
  return `最近一次处理失败：${summary}。系统最多自动重试 3 次；耗尽后会标记失败并提示。`
}

function notifyFailedIndexTask(record) {
  if (record.indexStatus !== 'FAILED') return false
  const taskId = String(record.indexTaskId || '')
  if (!taskId) return false
  const storageKey = `admin-knowledge-index-failed:${taskId}`
  if (notifiedFailedIndexTasks.has(taskId)) return false
  try {
    if (sessionStorage.getItem(storageKey)) {
      notifiedFailedIndexTasks.add(taskId)
      return false
    }
    sessionStorage.setItem(storageKey, '1')
  } catch {
    // 中文注释：浏览器禁用 sessionStorage 时，使用当前页面内存集合继续去重。
  }
  notifiedFailedIndexTasks.add(taskId)
  return true
}

function formatFailedIndexSummary(record) {
  const summary = String(record.indexError || '知识向量处理最终失败，可重新灌库后重试').slice(0, 260)
  return `知识向量索引在自动重试 3 次后仍失败：${summary}`
}

function hasPendingIndexJobs() {
  return list.value.some((record) => ['PENDING', 'PROCESSING', 'DELETE_PENDING'].includes(record.indexStatus))
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function watchIndexTask(initial, successMessage, onStatus = null, options = {}) {
  const docId = initial && initial.id
  const taskId = initial && initial.indexTaskId
  if (!docId || !taskId) return
  while (viewMounted) {
    try {
      const current = await fetchAdminKnowledgeTask(docId, taskId)
      if (!viewMounted) return
      const row = list.value.find((item) => String(item.id) === String(docId))
      if (row) Object.assign(row, current)
      if (current.indexStatus === 'READY' || current.indexStatus === 'DELETED') {
        if (typeof onStatus === 'function') onStatus('已入库', '')
        if (current.indexStatus === 'DELETED') await load({ silent: true })
        if (!options.silent && successMessage) toastSuccess(successMessage)
        return
      }
      if (current.indexStatus === 'FAILED') {
        if (typeof onStatus === 'function') {
          onStatus('处理失败', current.indexError || '知识向量索引失败')
        }
        if (!options.silent && notifyFailedIndexTask(current)) {
          toastError(formatFailedIndexSummary(current))
        }
        if (!options.silent) await load({ silent: true })
        return
      }
      if (typeof onStatus === 'function') {
        onStatus(current.indexStatus === 'PROCESSING' ? '向量处理中' : '等待向量处理', '')
      }
    } catch (e) {
      if (!viewMounted) return
      if (isAuthErrorHandled(e)) return
      if (e.status === 404) {
        // 中文注释：文档已有更新任务时，旧 taskId 会失效；重新读取当前文档状态后停止旧轮询。
        await load({ silent: true })
        return
      }
    }
    await delay(3000)
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
  selectedFiles.value = []
  uploadQueue.value = []
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
  fileList.value = items.slice(-MAX_KNOWLEDGE_UPLOAD_FILES)
  selectedFiles.value = fileList.value.map((item) => item && item.file).filter(Boolean)
  uploadQueue.value = []
  if (selectedFiles.value.length === 1 && !uploadForm.title) {
    const name = String(selectedFiles.value[0].name || '')
    uploadForm.title = name.replace(/\.(md|markdown|pdf)$/i, '')
  } else if (selectedFiles.value.length > 1) {
    uploadForm.title = ''
  }
}

function onFileRemove(fileItem) {
  const removedUid = fileItem && fileItem.uid
  fileList.value = removedUid == null
    ? []
    : fileList.value.filter((item) => String(item.uid) !== String(removedUid))
  selectedFiles.value = fileList.value.map((item) => item && item.file).filter(Boolean)
  uploadQueue.value = []
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
    let result
    if (editingId.value) {
      result = await updateAdminKnowledge(editingId.value, payload)
    } else {
      result = await createAdminKnowledge(payload)
    }
    toastSuccess(result.duplicate ? '正文重复，已复用现有知识文档' : '已提交向量索引任务')
    modalVisible.value = false
    load()
    if (['PENDING', 'PROCESSING', 'DELETE_PENDING'].includes(result.indexStatus)) {
      watchIndexTask(result, '知识向量索引完成')
    }
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function doUpload() {
  if (!selectedFiles.value.length) {
    toastError('请选择 MD 或 PDF 文件')
    return
  }
  uploading.value = true
  const files = selectedFiles.value.slice(0, MAX_KNOWLEDGE_UPLOAD_FILES)
  const queue = files.map((file, index) => ({
    id: `${file.name}-${file.size}-${file.lastModified || 0}-${index}`,
    file,
    name: String(file.name || `文件 ${index + 1}`),
    status: '等待提交',
    state: 'waiting',
    error: '',
    duplicate: false,
  }))
  uploadQueue.value = queue
  let cursor = 0
  let submitted = 0
  try {
    const runWorker = async () => {
      while (cursor < queue.length) {
        const item = queue[cursor++]
        if (!/\.(md|markdown|pdf)$/i.test(item.name)) {
          item.state = 'failed'
          item.status = '格式不支持'
          item.error = '仅支持 .md / .markdown / .pdf'
          continue
        }
        if (item.file.size > MAX_KNOWLEDGE_FILE_BYTES) {
          item.state = 'failed'
          item.status = '文件过大'
          item.error = '单份文件不能超过 25MB'
          continue
        }
        item.status = '正在上传和解析'
        item.state = 'processing'
        try {
          const result = await uploadAdminKnowledgeFile({
            file: item.file,
            title: files.length === 1 ? uploadForm.title : '',
            category: uploadForm.category,
            tags: parseTags(uploadTagsText.value),
          })
          submitted += 1
          item.duplicate = Boolean(result.duplicate)
          item.status = item.duplicate ? '重复正文，复用现有任务' : '等待向量处理'
          if (['PENDING', 'PROCESSING', 'DELETE_PENDING'].includes(result.indexStatus)) {
            watchIndexTask(result, '', (status, error) => {
              item.status = item.duplicate ? `重复文档：${status}` : status
              item.error = error
              item.state = status === '已入库' ? 'done' : status === '处理失败' ? 'failed' : 'processing'
            }, { silent: true })
          } else if (result.indexStatus === 'READY') {
            item.status = item.duplicate ? '重复文档：已入库' : '已入库'
            item.state = 'done'
          }
        } catch (error) {
          item.state = 'failed'
          item.status = '提交失败'
          item.error = error.message || '上传失败'
        }
      }
    }
    // 中文注释：浏览器最多同时提交两份，避免占满连接并造成上传请求内存峰值。
    await Promise.all(
      Array.from({ length: Math.min(KNOWLEDGE_UPLOAD_CONCURRENCY, queue.length) }, runWorker)
    )
    fileList.value = []
    selectedFiles.value = []
    const failed = queue.filter((item) => item.state === 'failed').length
    if (submitted > 0) {
      toastSuccess(`已提交 ${submitted} 份知识文件${failed ? `，${failed} 份失败，请查看明细` : ''}`)
      await load()
    }
    if (failed > 0) {
      const firstFailure = queue.find((item) => item.state === 'failed')
      if (firstFailure) toastError(`${firstFailure.name}：${firstFailure.error}`)
    }
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function doReindex(record) {
  try {
    const result = await reindexAdminKnowledge(record.id)
    toastSuccess('已提交重新灌库任务')
    watchIndexTask(result, '知识向量重新灌库完成')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function remove(record) {
  try {
    const result = await deleteAdminKnowledge(record.id)
    toastSuccess('已提交删除任务；向量清理完成后才标记为已移除')
    load()
    watchIndexTask(result, '知识向量已清理')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '删除失败')
  }
}

function reportBatchResult(actionName, result) {
  if (result.failed.length === 0) {
    toastSuccess(`已提交${actionName}任务 ${result.succeeded.length} 篇`)
    return
  }
  const reason = result.failed[0].error && result.failed[0].error.message
  const message = `${actionName}已提交 ${result.succeeded.length} 篇，提交失败 ${result.failed.length} 篇${reason ? `（${reason}）` : ''}`
  if (!isAuthErrorHandled(result.failed[0].error)) toastError(message)
}

async function reindexSelected() {
  if (batching.value || !selectedRecords.value.length) return
  batching.value = true
  try {
    const result = await runBatchOperations(selectedRecords.value, (record) => reindexAdminKnowledge(record.id))
    reportBatchResult('批量重新灌库', result)
    selectedKeys.value = []
    await load()
  } finally {
    batching.value = false
  }
}

async function removeSelected() {
  if (batching.value || !selectedRecords.value.length) return
  batching.value = true
  try {
    const result = await runBatchOperations(selectedRecords.value, (record) => deleteAdminKnowledge(record.id))
    reportBatchResult('批量删除知识', result)
    await load()
  } finally {
    batching.value = false
  }
}

onMounted(() => {
  viewMounted = true
  load()
  pollingTimer = setInterval(() => {
    if (hasPendingIndexJobs()) load({ silent: true })
  }, 3000)
})

onUnmounted(() => {
  viewMounted = false
  if (pollingTimer) clearInterval(pollingTimer)
})
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
.hint {
  margin: 8px 0 0;
  color: #86909c;
  font-size: 12px;
}
.knowledge-file-upload :deep(.arco-upload-progress) {
  display: none;
}
.upload-results {
  display: grid;
  gap: 8px;
  max-height: 220px;
  overflow: auto;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-2);
}
.upload-result-row { min-width: 0; }
.upload-result-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.upload-result-name { overflow: hidden; color: var(--color-text-1); text-overflow: ellipsis; white-space: nowrap; }
.upload-result-error { margin: 4px 0 0; color: var(--color-danger-6); font-size: 12px; }
</style>
