<template>
  <div class="ops">
    <a-alert type="info" show-icon style="margin-bottom: 16px">
      本页用于运维「热梗爬取」与「梗向量灌库」，与「知识库」文档 RAG 无关。日常审核请优先使用
      <a-link @click="$router.push({ name: 'memes' })">梗管理</a-link>
      。
    </a-alert>

    <a-card title="运行状态" :loading="statusLoading">
      <template #extra>
        <a-button size="mini" :loading="statusLoading" @click="loadStatus">刷新</a-button>
      </template>
      <a-space wrap size="large">
        <a-statistic title="Agent 配置">
          <template #value>
            <a-tag :color="status.agentConfigured ? 'green' : 'red'">
              {{ status.agentConfigured ? '已配置' : '未配置' }}
            </a-tag>
          </template>
        </a-statistic>
        <a-statistic title="Agent 可达">
          <template #value>
            <a-tag :color="status.agentReachable ? 'green' : 'orangered'">
              {{ status.agentReachable ? '正常' : '不可达' }}
            </a-tag>
          </template>
        </a-statistic>
        <a-statistic title="向量库">
          <template #value>
            <a-tag :color="vectorTagColor">{{ vectorLabel }}</a-tag>
          </template>
        </a-statistic>
        <a-statistic title="爬虫开关">
          <template #value>
            <a-tag :color="status.crawlEnabled ? 'green' : 'gray'">
              {{ status.crawlEnabled ? '启用' : '关闭' }}
            </a-tag>
          </template>
        </a-statistic>
        <a-statistic title="回填开关">
          <template #value>
            <a-tag :color="status.backfillEnabled ? 'green' : 'gray'">
              {{ status.backfillEnabled ? '启用' : '关闭' }}
            </a-tag>
          </template>
        </a-statistic>
      </a-space>
      <p v-if="status.agentBaseUrl" class="meta">
        {{ status.agentBaseUrl }}
        <template v-if="status.agentService"> · {{ status.agentService }}</template>
        <template v-if="status.agentEnv"> ({{ status.agentEnv }})</template>
        <template v-if="status.crawlScheduleEnabled"> · 定时爬取已开</template>
      </p>
    </a-card>

    <a-card title="热梗爬取" style="margin-top: 16px">
      <p class="desc">
        调用 MemeAgent 采集候选热梗，写入 MySQL 后异步灌向量。耗时可能较长，请勿重复点击。
      </p>
      <a-space wrap>
        <a-input-number
          v-model="crawlLimit"
          :min="1"
          :max="20"
          placeholder="条数"
          :disabled="!status.crawlEnabled"
        />
        <span class="hint">默认 {{ status.crawlDefaultLimit || 5 }}，上限 20</span>
        <a-popconfirm
          content="确认触发一次热梗爬取？可能调用外部爬虫并写库。"
          :disabled="!canCrawl"
          @ok="onCrawl"
        >
          <a-button type="primary" :loading="crawlLoading" :disabled="!canCrawl">
            触发爬取
          </a-button>
        </a-popconfirm>
        <a-button @click="$router.push({ name: 'memes', query: { status: '2' } })">
          查看待审核
        </a-button>
      </a-space>
      <a-descriptions
        v-if="crawlResult"
        :column="4"
        size="small"
        bordered
        style="margin-top: 12px"
      >
        <a-descriptions-item label="拉取">{{ crawlResult.fetched }}</a-descriptions-item>
        <a-descriptions-item label="新建">{{ crawlResult.created }}</a-descriptions-item>
        <a-descriptions-item label="跳过">{{ crawlResult.skipped }}</a-descriptions-item>
        <a-descriptions-item label="失败">{{ crawlResult.failed }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card title="向量回填" style="margin-top: 16px">
      <p class="desc">
        按 ID 升序扫描已发布梗并提交灌库队列，用于补齐历史向量。受
        <code>meme.ai.backfill-enabled</code> 控制。
      </p>
      <a-space wrap>
        <a-input-number
          v-model="backfillLimit"
          :min="1"
          :max="5000"
          placeholder="limit"
          :disabled="!status.backfillEnabled"
        />
        <a-popconfirm
          content="确认回填向量？将批量调用 Agent 灌库。"
          :disabled="!canBackfill"
          @ok="onBackfill"
        >
          <a-button type="primary" :loading="backfillLoading" :disabled="!canBackfill">
            开始回填
          </a-button>
        </a-popconfirm>
      </a-space>
      <a-descriptions
        v-if="backfillResult"
        :column="3"
        size="small"
        bordered
        style="margin-top: 12px"
      >
        <a-descriptions-item label="扫描">{{ backfillResult.scanned }}</a-descriptions-item>
        <a-descriptions-item label="入队">{{ backfillResult.queued }}</a-descriptions-item>
        <a-descriptions-item label="跳过">{{ backfillResult.skipped }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card title="单条同步 / 审核灌库" style="margin-top: 16px">
      <p class="desc">
        <strong>同步</strong>：status=已发布则灌库，否则删向量。
        <strong>审核灌库</strong>：将梗审为已发布并触发灌库（与梗管理「通过」同类）。
      </p>
      <a-space wrap>
        <a-input v-model="memeId" placeholder="memeId" style="width: 220px" allow-clear />
        <a-button :loading="syncLoading" :disabled="!memeId.trim()" @click="onSync">
          同步向量
        </a-button>
        <a-popconfirm
          content="确认审核通过该梗并灌库？"
          :disabled="!memeId.trim()"
          @ok="onApprove"
        >
          <a-button status="success" :loading="approveLoading" :disabled="!memeId.trim()">
            审核灌库
          </a-button>
        </a-popconfirm>
        <a-button
          type="outline"
          :disabled="!memeId.trim()"
          @click="$router.push({ name: 'memes', query: { keyword: memeId.trim() } })"
        >
          在梗管理中查找
        </a-button>
      </a-space>
      <a-descriptions
        v-if="singleResult"
        :column="2"
        size="small"
        bordered
        style="margin-top: 12px"
      >
        <a-descriptions-item v-for="(val, key) in singleResult" :key="key" :label="String(key)">
          {{ val }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  fetchAiOpsStatus,
  triggerAiCrawl,
  triggerAiIngestApprove,
  triggerAiIngestBackfill,
  triggerAiIngestSync,
} from '@/api/adminAiOps'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

const statusLoading = ref(false)
const crawlLoading = ref(false)
const backfillLoading = ref(false)
const syncLoading = ref(false)
const approveLoading = ref(false)

const crawlLimit = ref(5)
const backfillLimit = ref(100)
const memeId = ref('')

const crawlResult = ref(null)
const backfillResult = ref(null)
const singleResult = ref(null)

const status = reactive({
  agentConfigured: false,
  agentReachable: false,
  agentService: '',
  agentEnv: '',
  vectorDbOk: null,
  crawlEnabled: false,
  crawlScheduleEnabled: false,
  crawlDefaultLimit: 5,
  backfillEnabled: true,
  agentBaseUrl: '',
})

const canCrawl = computed(
  () => status.crawlEnabled && status.agentConfigured && status.agentReachable && !crawlLoading.value
)
const canBackfill = computed(
  () =>
    status.backfillEnabled &&
    status.agentConfigured &&
    status.agentReachable &&
    !backfillLoading.value
)

const vectorLabel = computed(() => {
  if (status.vectorDbOk === true) return '正常'
  if (status.vectorDbOk === false) return '异常'
  return '未知'
})
const vectorTagColor = computed(() => {
  if (status.vectorDbOk === true) return 'green'
  if (status.vectorDbOk === false) return 'red'
  return 'gray'
})

async function loadStatus() {
  statusLoading.value = true
  try {
    const data = await fetchAiOpsStatus()
    Object.assign(status, data || {})
    if (!crawlLimit.value || crawlLimit.value === 5) {
      crawlLimit.value = Number(data?.crawlDefaultLimit) || 5
    }
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '状态加载失败')
  } finally {
    statusLoading.value = false
  }
}

async function onCrawl() {
  crawlLoading.value = true
  try {
    const data = await triggerAiCrawl(crawlLimit.value)
    crawlResult.value = data
    toastSuccess(
      `爬取完成：拉取 ${data.fetched}，新建 ${data.created}，跳过 ${data.skipped}，失败 ${data.failed}`
    )
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '触发失败')
  } finally {
    crawlLoading.value = false
  }
}

async function onBackfill() {
  backfillLoading.value = true
  try {
    const data = await triggerAiIngestBackfill(backfillLimit.value)
    backfillResult.value = data
    toastSuccess(`回填完成：扫描 ${data.scanned}，入队 ${data.queued}`)
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '触发失败')
  } finally {
    backfillLoading.value = false
  }
}

async function onSync() {
  syncLoading.value = true
  try {
    const data = await triggerAiIngestSync(memeId.value)
    singleResult.value = data
    toastSuccess(data?.queued ? '已提交同步' : '未入队（可能已跳过）')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '同步失败')
  } finally {
    syncLoading.value = false
  }
}

async function onApprove() {
  approveLoading.value = true
  try {
    const data = await triggerAiIngestApprove(memeId.value)
    singleResult.value = data
    toastSuccess('审核灌库完成')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  } finally {
    approveLoading.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.desc {
  margin: 0 0 12px;
  color: #4e5969;
  font-size: 13px;
  line-height: 1.6;
}
.hint {
  color: #86909c;
  font-size: 12px;
}
.meta {
  margin: 12px 0 0;
  color: #86909c;
  font-size: 12px;
}
</style>
