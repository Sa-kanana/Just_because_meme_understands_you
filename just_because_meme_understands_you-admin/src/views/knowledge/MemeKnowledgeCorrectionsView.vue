<template>
  <div>
    <div class="toolbar">
      <a-select v-model="status" :options="statusOptions" style="width: 160px" @change="reload" />
      <a-button @click="reload">刷新</a-button>
    </div>
    <a-table row-key="id" :columns="columns" :data="rows" :loading="loading"
             :pagination="pagination" @page-change="changePage" @page-size-change="changeSize">
      <template #meme="{ record }">
        <div>{{ record.memeName || `梗 #${record.memeId}` }}</div>
        <div class="muted">ID {{ record.memeId }} · 提交者 {{ record.userId }}</div>
      </template>
      <template #changes="{ record }">
        <dl class="changes"><div v-for="(value, key) in record.changes" :key="key">
          <dt>{{ labels[key] || key }}</dt><dd>{{ displayValue(key, value) }}</dd>
        </div></dl>
      </template>
      <template #state="{ record }">
        <a-tag :color="record.status === 'PENDING' ? 'orange' : record.status === 'APPROVED' ? 'green' : 'gray'">
          {{ statusLabels[record.status] || record.status }}
        </a-tag>
        <div v-if="record.handlingNote" class="muted">{{ record.handlingNote }}</div>
      </template>
      <template #actions="{ record }">
        <a-space v-if="record.status === 'PENDING'">
          <a-button size="mini" type="text" @click="openDecision(record, 'REJECTED')">驳回</a-button>
          <a-button size="mini" type="text" status="success" @click="openDecision(record, 'APPROVED')">通过并更新</a-button>
        </a-space>
      </template>
    </a-table>
    <a-modal v-model:visible="dialogVisible" :title="decision === 'APPROVED' ? '通过纠错并更新资料' : '驳回纠错'"
             :ok-loading="saving" @ok="submitDecision">
      <p>申请 #{{ selected && selected.id }} · {{ selected && selected.memeName }}</p>
      <a-textarea v-model="note" placeholder="审核备注（选填，最多 500 字）" :max-length="500" show-word-limit />
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { fetchMemeKnowledgeCorrections, resolveMemeKnowledgeCorrection } from '@/api/adminMemeKnowledge'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const labels = {
  sourceUrl: '出处 URL', firstSeenDate: '首次出现', popularityContext: '流行语境',
  usageExamples: '用法示例', evolution: '演变', relatedMemeIds: '相关梗 ID',
}
const statusLabels = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
const statusOptions = [
  { label: '待审核', value: 'PENDING' }, { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' }, { label: '全部', value: '' },
]
const columns = [
  { title: '申请 ID', dataIndex: 'id', width: 100 },
  { title: '梗', slotName: 'meme', width: 180 },
  { title: '建议修改', slotName: 'changes' },
  { title: '提交时间', dataIndex: 'createdAt', width: 175 },
  { title: '状态', slotName: 'state', width: 140 },
  { title: '操作', slotName: 'actions', width: 180 },
]
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const status = ref('PENDING')
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true })
const dialogVisible = ref(false)
const selected = ref(null)
const decision = ref('APPROVED')
const note = ref('')

function displayValue(key, value) {
  if (value == null || value === '') return '清空此字段'
  if (key === 'relatedMemeIds') return Array.isArray(value) && value.length ? value.join('、') : '清空关联'
  return String(value)
}
async function load() {
  loading.value = true
  try {
    const data = await fetchMemeKnowledgeCorrections({ page: pagination.current, size: pagination.pageSize, status: status.value })
    rows.value = Array.isArray(data.list) ? data.list : []
    pagination.total = Number(data.total) || 0
  } catch (e) { if (!isAuthErrorHandled(e)) toastError(e.message || '加载纠错申请失败') }
  finally { loading.value = false }
}
function reload() { pagination.current = 1; load() }
function changePage(page) { pagination.current = page; load() }
function changeSize(size) { pagination.pageSize = size; pagination.current = 1; load() }
function openDecision(record, value) {
  selected.value = record
  decision.value = value
  note.value = ''
  dialogVisible.value = true
}
async function submitDecision() {
  if (!selected.value || saving.value) return
  saving.value = true
  try {
    await resolveMemeKnowledgeCorrection(selected.value.id, decision.value, note.value.trim())
    toastSuccess(decision.value === 'APPROVED' ? '资料已更新' : '纠错已驳回')
    dialogVisible.value = false
    load()
  } catch (e) { if (!isAuthErrorHandled(e)) toastError(e.message || '审核失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.muted { margin-top: 4px; color: var(--color-text-3); font-size: 12px; }
.changes { margin: 0; display: grid; gap: 6px; }
.changes > div { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 8px; }
.changes dt { color: var(--color-text-3); }
.changes dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
