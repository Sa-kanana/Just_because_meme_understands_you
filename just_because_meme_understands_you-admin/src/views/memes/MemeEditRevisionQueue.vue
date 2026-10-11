<template>
  <section class="revision-page">
    <header class="page-intro">
      <div>
        <p class="eyebrow">CONTENT REVIEW</p>
        <h2>梗内容修改审核</h2>
        <p>作者修改已发布或下架的梗后，会先进入这里审核；通过后才替换正式内容。</p>
      </div>
      <a-button :loading="loading" @click="reload">刷新队列</a-button>
    </header>

    <a-card class="queue-card" :bordered="false">
      <a-table
        row-key="revisionId"
        :loading="loading"
        :columns="columns"
        :data="rows"
        :pagination="pagination"
        @page-change="changePage"
        @page-size-change="changeSize"
      >
        <template #empty>
          <a-empty description="目前没有待审核的修改稿" />
        </template>
        <template #meme="{ record }">
          <strong>{{ record.memeName || '未命名梗' }}</strong>
          <div class="muted">梗 ID {{ record.memeId }} · 作者 ID {{ record.editorUserId }}</div>
        </template>
        <template #status="{ record }">
          <a-tag :color="memeStatusColor(record.memeStatus)">
            {{ memeStatusLabel(record.memeStatus) }}
          </a-tag>
        </template>
        <template #actions="{ record }">
          <a-button type="text" size="small" @click="openDetail(record)">查看并审核</a-button>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:visible="detailVisible"
      title="修改内容对照"
      width="min(1120px, calc(100vw - 32px))"
      :mask-closable="!reviewing"
      :footer="false"
      @close="closeDetail"
    >
      <div v-if="detailLoading" class="detail-loading"><a-spin tip="正在读取内容对照…" /></div>
      <template v-else-if="detail">
        <div class="detail-heading">
          <div>
            <h3>{{ detail.memeName || '未命名梗' }}</h3>
            <p>修改稿 #{{ detail.revisionId }} · 梗 #{{ detail.memeId }} · 作者 #{{ detail.editorUserId }}</p>
          </div>
          <a-tag :color="detail.status === 'PENDING' ? 'orange' : 'gray'">
            {{ detail.status === 'PENDING' ? '待审核' : revisionStatusLabel(detail.status) }}
          </a-tag>
        </div>

        <div class="compare-grid">
          <a-card class="content-card" title="当前正式内容" :bordered="false">
            <ContentSnapshot :content="detail.currentContent" />
          </a-card>
          <a-card class="content-card content-card--submitted" title="作者提交的修改" :bordered="false">
            <ContentSnapshot :content="detail.submittedContent" />
          </a-card>
        </div>

        <a-form v-if="rejectMode" layout="vertical" class="reject-form">
          <a-form-item label="驳回原因" required>
            <a-textarea
              v-model="rejectNote"
              :max-length="500"
              show-word-limit
              :auto-size="{ minRows: 3, maxRows: 6 }"
              placeholder="说明需要修改的内容，作者可在个人主页查看后重新提交"
            />
          </a-form-item>
        </a-form>

        <footer class="review-actions">
          <a-button :disabled="reviewing" @click="closeDetail">关闭</a-button>
          <span v-if="detail.status !== 'PENDING'" class="muted review-complete-note">该修改稿已处理，当前不可重复审核。</span>
          <template v-else-if="rejectMode">
            <a-button :disabled="reviewing" @click="rejectMode = false">返回</a-button>
            <a-button status="danger" type="primary" :loading="reviewing" @click="submitDecision('REJECTED')">
              确认驳回
            </a-button>
          </template>
          <template v-else>
            <a-button status="danger" :disabled="reviewing" @click="rejectMode = true">驳回修改</a-button>
            <a-popconfirm content="通过后将以修改稿替换当前可编辑内容，是否继续？" @ok="submitDecision('APPROVED')">
              <a-button type="primary" status="success" :loading="reviewing">通过修改</a-button>
            </a-popconfirm>
          </template>
        </footer>
      </template>
    </a-modal>
  </section>
</template>

<script setup>
import { defineComponent, h, onMounted, reactive, ref } from 'vue'
import { fetchAdminMemeEditRevision, fetchAdminMemeEditRevisions, reviewAdminMemeEditRevision } from '@/api/adminMemeEditRevision'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'

/** 中文注释：把审核快照字段显示为可比较的基础信息、标签、媒体与链接。 */
const ContentSnapshot = defineComponent({
  name: 'ContentSnapshot',
  props: { content: { type: Object, default: null } },
  setup(props) {
    return () => {
      const content = props.content || {}
      const tags = Array.isArray(content.tagIds) ? content.tagIds : [] // 中文注释：快照中的关联标签 ID。
      const media = Array.isArray(content.resourceUrls) ? content.resourceUrls : [] // 中文注释：快照中的媒体资源地址。
      const links = Array.isArray(content.resources) ? content.resources : [] // 中文注释：快照中的外部链接。
      return h('div', { class: 'snapshot' }, [
        h('img', { class: 'snapshot-cover', src: content.image || '', alt: '梗封面' }),
        h('h4', content.name || '未填写名称'),
        h('p', { class: 'snapshot-introduction' }, content.introduction || '暂无介绍'),
        h('div', { class: 'snapshot-section' }, [
          h('span', { class: 'snapshot-label' }, '标签 ID'),
          h('span', tags.length ? tags.join('、') : '无'),
        ]),
        h('div', { class: 'snapshot-section' }, [
          h('span', { class: 'snapshot-label' }, `媒体资源（${media.length}）`),
          ...(media.length ? media.map((url, index) => h('a', { key: `media-${index}`, href: url, target: '_blank', rel: 'noreferrer' }, `查看资源 ${index + 1}`)) : [h('span', '无')]),
        ]),
        h('div', { class: 'snapshot-section snapshot-links' }, [
          h('span', { class: 'snapshot-label' }, `相关链接（${links.length}）`),
          ...(links.length ? links.map((item, index) => h('a', { key: `link-${index}`, href: item.url, target: '_blank', rel: 'noreferrer' }, item.title || item.url)) : [h('span', '无')]),
        ]),
      ])
    }
  },
})

/** 中文注释：审核队列表格列定义。 */
const columns = [
  { title: '修改稿', slotName: 'meme', minWidth: 220 },
  { title: '当前状态', slotName: 'status', width: 130 },
  { title: '提交时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', slotName: 'actions', width: 130 },
]
const rows = ref([]) // 中文注释：当前审核页中的待审修改稿。
const loading = ref(false) // 中文注释：审核队列加载状态。
const detailLoading = ref(false) // 中文注释：审核详情加载状态。
const detailVisible = ref(false) // 中文注释：修改稿对照弹窗显示状态。
const reviewing = ref(false) // 中文注释：审核提交状态。
const rejectMode = ref(false) // 中文注释：当前是否正在填写驳回原因。
const rejectNote = ref('') // 中文注释：管理员填写的驳回说明。
const detail = ref(null) // 中文注释：当前打开的修改稿对照详情。
const pagination = reactive({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true }) // 中文注释：审核队列分页配置。

async function load() {
  loading.value = true
  try {
    const result = await fetchAdminMemeEditRevisions({ page: pagination.current, size: pagination.pageSize })
    rows.value = Array.isArray(result?.list) ? result.list : []
    pagination.total = Number(result?.total) || 0
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '加载修改稿审核队列失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  pagination.current = 1
  load()
}

function changePage(page) {
  pagination.current = page
  load()
}

function changeSize(size) {
  pagination.pageSize = size
  pagination.current = 1
  load()
}

/** 中文注释：把修改稿审核状态转换成面向管理员的提示文字。 */
function revisionStatusLabel(status) {
  return ({ APPROVED: '已通过', REJECTED: '已驳回', CANCELED: '已取消' })[String(status || '').toUpperCase()] || '已处理'
}

/** 中文注释：显示审核队列对应梗当前的准确生命周期状态。 */
function memeStatusLabel(status) {
  return ({
    1: '当前已发布',
    2: '梗审核中',
    3: '当前已下架',
    4: '梗已删除',
    5: '风控锁定',
    6: '恢复审核中',
  })[Number(status)] || '状态未知'
}

/** 中文注释：用克制的颜色区分已发布、已下架和受限状态。 */
function memeStatusColor(status) {
  const value = Number(status)
  if (value === 1) return 'green'
  if (value === 3) return 'gray'
  if (value === 4 || value === 5) return 'red'
  return 'orange'
}

async function openDetail(record) {
  detailVisible.value = true
  detailLoading.value = true
  rejectMode.value = false
  rejectNote.value = ''
  detail.value = null
  try {
    detail.value = await fetchAdminMemeEditRevision(record.revisionId)
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '读取修改稿详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  if (reviewing.value) return
  detailVisible.value = false
  rejectMode.value = false
  rejectNote.value = ''
  detail.value = null
}

async function submitDecision(status) {
  if (!detail.value || reviewing.value) return
  if (status === 'REJECTED' && !rejectNote.value.trim()) {
    toastError('请填写驳回原因，方便作者修改后重新提交')
    return
  }
  reviewing.value = true
  try {
    const result = await reviewAdminMemeEditRevision(detail.value.revisionId, status, rejectNote.value)
    if (result?.status === 'CANCELED') {
      toastError(result.reviewNote || '梗的状态已发生变化，修改稿已取消')
    } else {
      toastSuccess(status === 'APPROVED' ? '修改稿已通过，正式内容已更新' : '已驳回修改稿')
    }
    if (rows.value.length <= 1 && pagination.current > 1) pagination.current -= 1
    detailVisible.value = false
    detail.value = null
    await load()
  } catch (error) {
    if (!isAuthErrorHandled(error)) toastError(error.message || '提交审核结果失败')
  } finally {
    reviewing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.revision-page { display: flex; flex-direction: column; gap: 18px; }
.page-intro { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.page-intro h2 { margin: 0 0 6px; color: #202d42; font-size: 20px; }
.page-intro p { margin: 0; color: #7b8798; font-size: 13px; line-height: 1.55; }
.page-intro .eyebrow { margin-bottom: 5px; color: #5579c9; font-size: 10px; font-weight: 700; letter-spacing: .13em; }
.queue-card { border-radius: 14px; box-shadow: 0 4px 18px rgba(35, 55, 90, .04); }
.muted { margin-top: 4px; color: #929cad; font-size: 12px; }
.detail-loading { display: grid; min-height: 240px; place-items: center; }
.detail-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.detail-heading h3 { margin: 0 0 5px; color: #25334b; font-size: 17px; }
.detail-heading p { margin: 0; color: #8894a5; font-size: 12px; }
.compare-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.content-card { min-width: 0; border: 1px solid #e9edf3; border-radius: 12px; background: #fff; }
.content-card--submitted { border-color: #c8d8ff; background: #fbfcff; }
.content-card :deep(.snapshot) { min-width: 0; }
.content-card :deep(.snapshot-cover) { display: block; width: 100%; max-height: 210px; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 9px; background: #f1f3f7; }
.content-card :deep(.snapshot h4) { margin: 12px 0 5px; color: #25334b; font-size: 15px; }
.content-card :deep(.snapshot-introduction) { min-height: 42px; margin: 0; color: #657286; font-size: 13px; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.content-card :deep(.snapshot-section) { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; color: #4e5c70; font-size: 12px; }
.content-card :deep(.snapshot-section a) { color: #376dcc; overflow-wrap: anywhere; }
.content-card :deep(.snapshot-label) { width: 100%; color: #929cad; font-size: 11px; }
.content-card :deep(.snapshot-links) { flex-direction: column; }
.reject-form { margin-top: 16px; }
.review-actions { display: flex; justify-content: flex-end; gap: 9px; margin-top: 20px; padding-top: 16px; border-top: 1px solid #edf0f4; }
.review-complete-note { align-self: center; margin-right: auto; }
@media (max-width: 720px) {
  .page-intro { align-items: flex-start; flex-direction: column; }
  .compare-grid { grid-template-columns: 1fr; }
  .content-card :deep(.snapshot-cover) { max-height: 180px; }
}
</style>
