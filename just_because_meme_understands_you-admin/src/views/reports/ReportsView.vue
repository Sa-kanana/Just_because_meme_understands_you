<template>
  <div>
    <div class="toolbar">
      <a-select v-model="status" :options="statusOptions" style="width: 160px" @change="reload" />
      <a-button @click="reload">刷新</a-button>
    </div>
    <a-table
      row-key="id"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :pagination="pagination"
      @page-change="changePage"
      @page-size-change="changeSize"
    >
      <template #target="{ record }">
        <a-tag :color="record.targetType === 'COMMENT' ? 'purple' : 'blue'">
          {{ record.targetType === 'COMMENT' ? '评论举报' : '梗举报' }}
        </a-tag>
        <router-link :to="{ name: 'memes', query: { keyword: record.memeName || '' } }">
          {{ record.memeName || `梗 #${record.memeId}` }}
        </router-link>
        <div class="muted">ID {{ record.memeId }} · 状态 {{ record.memeStatus ?? '已删除' }}</div>
        <blockquote v-if="record.targetType === 'COMMENT'" class="comment-context">
          {{ record.commentContent || '评论已删除或内容不可用' }}
        </blockquote>
      </template>
      <template #author="{ record }">
        <div>{{ record.targetAuthorName || record.commentAuthorName || '未知用户' }}</div>
        <div class="muted">ID {{ record.targetAuthorId || record.commentAuthorId || '-' }}</div>
      </template>
      <template #reporter="{ record }">
        <div>{{ record.reporterName || '未知用户' }}</div>
        <div class="muted">ID {{ record.reporterUserId }}</div>
      </template>
      <template #reason="{ record }">
        <div>{{ reasonLabels[record.reason] || record.reason }}</div>
        <div v-if="record.detail" class="detail">{{ record.detail }}</div>
      </template>
      <template #state="{ record }">
        <a-tag :color="record.status === 'PENDING' ? 'orange' : 'green'">
          {{ record.status === 'PENDING' ? '待处理' : '已处理' }}
        </a-tag>
        <div v-if="record.result" class="muted">{{ resultLabels[record.result] || record.result }}</div>
        <div v-if="record.handledBy" class="muted">处理人 ID {{ record.handledBy }}</div>
        <div v-if="record.handledAt" class="muted">处理时间 {{ record.handledAt }}</div>
        <div v-if="record.handlingNote" class="muted">{{ record.handlingNote }}</div>
      </template>
      <template #actions="{ record }">
        <a-space v-if="record.status === 'PENDING'">
          <a-button type="text" size="mini" @click="openResolution(record, 'DISMISSED')">驳回举报</a-button>
          <a-button v-if="record.targetType === 'MEME'" type="text" size="mini" status="danger" @click="openResolution(record, 'OFFLINED')">下架梗</a-button>
          <a-button v-else type="text" size="mini" status="danger" @click="openResolution(record, 'COMMENT_HIDDEN')">隐藏评论</a-button>
        </a-space>
      </template>
    </a-table>
    <a-modal
      v-model:visible="dialogVisible"
      :title="decisionTitle"
      :ok-loading="saving"
      @ok="submitResolution"
    >
      <p>举报 #{{ selected && selected.id }} · {{ selected && selected.targetType === 'COMMENT' ? '评论' : '梗' }} · {{ selected && selected.memeName }}</p>
      <blockquote v-if="selected && selected.targetType === 'COMMENT'" class="comment-context">{{ selected.commentContent }}</blockquote>
      <a-textarea v-model="note" placeholder="处理备注（选填，最多 500 字）" :max-length="500" show-word-limit />
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchAdminReports, resolveAdminReport } from '@/api/adminReport'
import { resolutionSuccessMessage } from '@/utils/reportFeedback.mjs'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '已处理', value: 'RESOLVED' },
  { label: '全部', value: '' },
]
const reasonLabels = {
  SPAM: '垃圾广告', ABUSE: '辱骂骚扰', COPYRIGHT: '版权侵权',
  PORNOGRAPHY: '色情低俗', VIOLENCE: '暴力血腥', OTHER: '其他',
}
const resultLabels = { DISMISSED: '举报不成立', OFFLINED: '梗已下架', COMMENT_HIDDEN: '评论已隐藏' }
const columns = [
  { title: '举报 ID', dataIndex: 'id', width: 105 },
  { title: '举报对象与上下文', slotName: 'target', width: 300 },
  { title: '内容作者', slotName: 'author', width: 150 },
  { title: '举报人', slotName: 'reporter', width: 150 },
  { title: '原因与说明', slotName: 'reason', width: 190 },
  { title: '提交时间', dataIndex: 'createdAt', width: 180 },
  { title: '处理记录', slotName: 'state', width: 190 },
  { title: '操作', slotName: 'actions', width: 175 },
]
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const status = ref('PENDING')
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true })
const dialogVisible = ref(false)
const selected = ref(null)
const decision = ref('DISMISSED')
const note = ref('')
const decisionTitle = computed(() => decision.value === 'OFFLINED' ? '确认下架梗'
  : decision.value === 'COMMENT_HIDDEN' ? '确认隐藏评论' : '确认驳回举报')

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminReports({ page: pagination.current, size: pagination.pageSize, status: status.value })
    rows.value = Array.isArray(data.list) ? data.list : []
    pagination.total = Number(data.total) || 0
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '加载举报失败')
  } finally {
    loading.value = false
  }
}
function reload() { pagination.current = 1; load() }
function changePage(page) { pagination.current = page; load() }
function changeSize(size) { pagination.pageSize = size; pagination.current = 1; load() }
function openResolution(record, result) {
  selected.value = record
  decision.value = result
  note.value = ''
  dialogVisible.value = true
}
async function submitResolution() {
  if (!selected.value || saving.value) return
  saving.value = true
  try {
    await resolveAdminReport(selected.value.id, decision.value, note.value.trim())
    toastSuccess(resolutionSuccessMessage(decision.value))
    dialogVisible.value = false
    load()
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '处理失败')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.muted { margin-top: 4px; color: var(--color-text-3); font-size: 12px; }
.detail { white-space: pre-wrap; overflow-wrap: anywhere; margin-top: 4px; }
.comment-context { margin: 8px 0 2px; padding: 8px 10px; border-left: 3px solid var(--color-primary-light-4); border-radius: 0 6px 6px 0; color: var(--color-text-2); background: var(--color-fill-2); white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
