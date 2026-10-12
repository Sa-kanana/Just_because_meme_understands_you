<template>
  <div class="ops">
    <a-card class="status-card" title="运行状态" :loading="statusLoading">
      <template #extra>
        <a-button size="mini" :loading="statusLoading" @click="loadStatus">刷新</a-button>
      </template>
      <div class="status-grid">
        <div class="status-tile">
          <span class="status-label">Agent 配置</span>
          <a-tag :color="status.agentConfigured ? 'green' : 'red'">
            {{ status.agentConfigured ? '已配置' : '未配置' }}
          </a-tag>
        </div>
        <div class="status-tile">
          <span class="status-label">Agent 可达</span>
          <a-tag :color="status.agentReachable ? 'green' : 'orangered'">
            {{ status.agentReachable ? '正常' : '不可达' }}
          </a-tag>
        </div>
        <div class="status-tile">
          <span class="status-label">向量库</span>
          <a-tag :color="vectorTagColor">{{ vectorLabel }}</a-tag>
        </div>
        <div class="status-tile">
          <span class="status-label">爬虫开关</span>
          <a-tag :color="status.crawlEnabled ? 'green' : 'gray'">
            {{ status.crawlEnabled ? '启用' : '关闭' }}
          </a-tag>
        </div>
      </div>
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

    <AiVectorReconciliationCard style="margin-top: 16px" />

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  fetchAiOpsStatus,
  triggerAiCrawl,
} from '@/api/adminAiOps'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'
import AiVectorReconciliationCard from './AiVectorReconciliationCard.vue'

const statusLoading = ref(false)
const crawlLoading = ref(false)

const crawlLimit = ref(5)

const crawlResult = ref(null)

const status = reactive({
  agentConfigured: false,
  agentReachable: false,
  vectorDbOk: null,
  crawlEnabled: false,
  crawlDefaultLimit: 5,
})

const canCrawl = computed(
  () => status.crawlEnabled && status.agentConfigured && status.agentReachable && !crawlLoading.value
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

onMounted(loadStatus)
</script>

<style scoped>
.status-card {
  border-radius: 12px;
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.status-tile {
  min-width: 0;
  min-height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-neutral-3);
  border-radius: 10px;
  background: linear-gradient(145deg, #fff 10%, #f7f9fc 100%);
}
.status-label {
  color: #4e5969;
  font-size: 14px;
  font-weight: 500;
}
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
@media (max-width: 960px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .status-grid {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .status-tile {
    min-height: 64px;
    padding: 12px 14px;
  }
}
</style>
