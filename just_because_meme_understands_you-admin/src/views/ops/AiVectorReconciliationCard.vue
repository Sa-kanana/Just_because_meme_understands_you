<template>
  <a-card title="梗向量库校准">
    <p class="desc">
      管理员手动执行 Java 已发布梗与 MemeAgent 向量 ID 的双向校准。完成表示同步事件已进入 Java Outbox，后续由异步投递写入向量库。
    </p>
    <a-space wrap>
      <a-tag v-if="reconciliationJob" :color="statusColor">
        {{ vectorReconciliationStatusLabel(reconciliationJob.status) }}
      </a-tag>
      <a-popconfirm
        content="确认启动？缺失或变更的梗以及超过 6 个分块的旧梗可能重新调用 Embedding；内容未变且分块数合规的梗会跳过。"
        :disabled="!canStart"
        @ok="startReconciliation"
      >
        <a-button type="primary" :loading="startLoading" :disabled="!canStart">
          开始双向校准
        </a-button>
      </a-popconfirm>
      <a-button
        v-if="canResumeVectorReconciliation(reconciliationJob?.status)"
        :loading="resumeLoading"
        :disabled="resumeLoading"
        @click="resumeReconciliation"
      >
        从失败位置继续
      </a-button>
      <a-button
        v-if="reconciliationJob"
        :loading="statusLoading"
        @click="refreshReconciliation(false)"
      >
        刷新进度
      </a-button>
    </a-space>
    <a-descriptions
      v-if="reconciliationJob"
      :column="4"
      size="small"
      bordered
      style="margin-top: 12px"
    >
      <a-descriptions-item label="任务 ID">{{ reconciliationJob.jobId }}</a-descriptions-item>
      <a-descriptions-item label="当前阶段">{{ phaseLabel }}</a-descriptions-item>
      <a-descriptions-item label="Java 已扫描">{{ reconciliationJob.publishedScanned ?? 0 }}</a-descriptions-item>
      <a-descriptions-item label="Upsert 事件入队">{{ reconciliationJob.upsertQueued ?? 0 }}</a-descriptions-item>
      <a-descriptions-item label="向量 ID 已扫描">{{ reconciliationJob.vectorScanned ?? 0 }}</a-descriptions-item>
      <a-descriptions-item label="删除事件入队">{{ reconciliationJob.removalQueued ?? 0 }}</a-descriptions-item>
      <a-descriptions-item label="失败阶段">{{ reconciliationJob.failureStage || '—' }}</a-descriptions-item>
      <a-descriptions-item label="更新时间">{{ reconciliationJob.updatedAt || '—' }}</a-descriptions-item>
    </a-descriptions>
  </a-card>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  fetchAiVectorReconciliationJob,
  resumeAiVectorReconciliation,
  startAiVectorReconciliation,
} from '@/api/adminAiOps'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'
import {
  canResumeVectorReconciliation,
  isVectorReconciliationActive,
  vectorReconciliationStatusLabel,
} from '@/utils/aiVectorReconciliation.mjs'

/** 中文注释：页面刷新后通过本地保存的任务 ID 恢复校准状态。 */
const VECTOR_RECONCILIATION_STORAGE_KEY = 'admin.aiOps.vectorReconciliationJobId'
/** 中文注释：管理端任务进度轮询间隔为 3 秒。 */
const RECONCILIATION_POLL_INTERVAL_MS = 3000

/** 中文注释：当前展示的向量校准任务。 */
const reconciliationJob = ref(null)
/** 中文注释：查询任务状态时显示的加载状态。 */
const statusLoading = ref(false)
/** 中文注释：启动任务时禁用重复请求。 */
const startLoading = ref(false)
/** 中文注释：继续任务时禁用重复请求。 */
const resumeLoading = ref(false)
/** 中文注释：记录定时器，在离开页面时停止轮询。 */
const pollTimer = ref(null)
/** 中文注释：阻止前一个请求未结束时重复轮询。 */
let pollBusy = false

/** 中文注释：活动任务期间不允许重复创建；已失败任务可由单独按钮续跑。 */
const canStart = computed(() => !isVectorReconciliationActive(reconciliationJob.value?.status)
  && !startLoading.value && !resumeLoading.value)
/** 中文注释：按后台任务状态显示适合管理端的标签颜色。 */
const statusColor = computed(() => {
  const status = String(reconciliationJob.value?.status || '').toUpperCase()
  if (status === 'COMPLETED') return 'green'
  if (status === 'FAILED') return 'red'
  if (status === 'RUNNING') return 'blue'
  return 'orange'
})
/** 中文注释：将数据库阶段编码映射为可读的管理端文案。 */
const phaseLabel = computed(() => {
  if (reconciliationJob.value?.phase === 'SYNC_PUBLISHED') return '同步已发布梗'
  if (reconciliationJob.value?.phase === 'CLEAN_ORPHAN') return '清理孤儿向量'
  return '—'
})

/** 中文注释：缓存最近任务 ID，供刷新页面和重新打开管理页后恢复进度。 */
function saveJob(job) {
  reconciliationJob.value = job || null
  if (!job?.jobId) return
  try {
    window.localStorage.setItem(VECTOR_RECONCILIATION_STORAGE_KEY, String(job.jobId))
  } catch {
    // 中文注释：本地存储不可用时仍保留当前页面内的任务状态。
  }
}

/** 中文注释：按任务 ID 查询当前进度；后台轮询错误静默处理，手动刷新错误提示管理员。 */
async function loadJob(jobId, quiet = true) {
  if (!jobId || pollBusy) return
  pollBusy = true
  statusLoading.value = true
  try {
    const job = await fetchAiVectorReconciliationJob(jobId)
    saveJob(job)
    if (isVectorReconciliationActive(job?.status)) startPolling()
    else stopPolling()
  } catch (error) {
    if (!quiet && !isAuthErrorHandled(error)) toastError(error.message || '获取校准进度失败')
  } finally {
    statusLoading.value = false
    pollBusy = false
  }
}

/** 中文注释：使用当前任务 ID 手动刷新状态。 */
function refreshReconciliation(quiet = true) {
  return loadJob(reconciliationJob.value?.jobId, quiet)
}

/** 中文注释：仅在页面挂载期间轮询活动任务。 */
function startPolling() {
  if (pollTimer.value != null) return
  pollTimer.value = window.setInterval(() => {
    if (isVectorReconciliationActive(reconciliationJob.value?.status)) refreshReconciliation(true)
    else stopPolling()
  }, RECONCILIATION_POLL_INTERVAL_MS)
}

/** 中文注释：离开页面或任务结束后释放轮询定时器。 */
function stopPolling() {
  if (pollTimer.value == null) return
  window.clearInterval(pollTimer.value)
  pollTimer.value = null
}

/** 中文注释：管理员手动开始全量双向校准，并保存返回的任务 ID。 */
async function startReconciliation() {
  startLoading.value = true
  try {
    const job = await startAiVectorReconciliation()
    saveJob(job)
    if (isVectorReconciliationActive(job?.status)) startPolling()
    toastSuccess('双向校准任务已提交')
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '启动校准失败')
  } finally {
    startLoading.value = false
  }
}

/** 中文注释：从失败时保存的游标恢复扫描，不重新处理已确认页。 */
async function resumeReconciliation() {
  if (!reconciliationJob.value?.jobId) return
  resumeLoading.value = true
  try {
    const job = await resumeAiVectorReconciliation(reconciliationJob.value.jobId)
    saveJob(job)
    if (isVectorReconciliationActive(job?.status)) startPolling()
    toastSuccess('校准任务已继续')
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '继续校准失败')
  } finally {
    resumeLoading.value = false
  }
}

onMounted(async () => {
  try {
    const storedJobId = window.localStorage.getItem(VECTOR_RECONCILIATION_STORAGE_KEY)
    if (storedJobId) await loadJob(storedJobId, false)
  } catch {
    // 中文注释：没有历史任务或浏览器存储不可用时不影响运维页面使用。
  }
})

onUnmounted(stopPolling)
</script>

<style scoped>
.desc {
  margin: 0 0 12px;
  color: #4e5969;
  font-size: 13px;
  line-height: 1.6;
}
</style>
